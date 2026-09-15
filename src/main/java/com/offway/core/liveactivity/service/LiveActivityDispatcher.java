package com.offway.core.liveactivity.service;

import com.offway.core.liveactivity.infrastructure.apns.ApnsResult;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivitySender;
import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.service.dto.LiveActivityTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 잠금화면 카드들에 오늘 값을 내보내고, <b>쓸모없어진 등록을 걷어낸다</b>(#575).
 *
 * <p><b>트랜잭션 밖에서 부른다.</b> 외부 호출이라 트랜잭션 안에 넣으면 DB 커넥션을 발송 시간만큼 잡아
 * 풀이 마른다(persistence-convention).
 */
@Slf4j
@Service
public class LiveActivityDispatcher {

    /**
     * 동시에 열어 둘 발송 수.
     *
     * <p>순차로 돌면 지연이 카드 수만큼 곱해지고, 무제한이면 APNs 쪽 속도 제한을 스스로 부른다.
     * 기존 FCM 발송과 같은 값을 쓴다 — <b>같은 팬아웃인데 경로마다 다르게 두면</b> 어느 쪽이 느린지
     * 비교할 수 없다.
     */
    private static final int MAX_CONCURRENT_SENDS = 16;

    private final LiveActivitySender liveActivitySender;
    private final LiveActivityTokenRepository liveActivityTokenRepository;

    /** 발송 전용 풀. 요청 처리 스레드를 쓰면 배치가 사용자 요청과 자원을 다툰다. */
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public LiveActivityDispatcher(
            LiveActivitySender liveActivitySender, LiveActivityTokenRepository liveActivityTokenRepository) {
        this.liveActivitySender = liveActivitySender;
        this.liveActivityTokenRepository = liveActivityTokenRepository;
    }

    /**
     * 대상들에게 보내고, 죽었거나 끝난 등록을 지운다.
     *
     * <p><b>속도 제한을 만나면 그 자리에서 멈춘다.</b> {@code 429} 는 상대가 지금 우리 전체를 밀어내는
     * 중이라는 뜻이라, 남은 것을 이어서 쏘면 제한만 길어지고 어차피 다 실패한다. 남긴 등록은 다음 자정에
     * 다시 대상이 된다 — 하루 한 번 도는 배치라 그것으로 충분하다.
     *
     * @return 실제로 보낸 건수
     */
    public int dispatch(List<LiveActivityTarget> targets) {
        if (targets.isEmpty()) {
            log.info("잠금화면 갱신 — 대상 없음");
            return 0;
        }

        Semaphore inFlight = new Semaphore(MAX_CONCURRENT_SENDS);
        // 한 건이 속도 제한을 만나면 아직 시작 안 한 나머지가 보고 비켜선다.
        AtomicBoolean throttled = new AtomicBoolean();
        List<CompletableFuture<Sent>> sending = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> send(target, inFlight, throttled), sendExecutor))
                .toList();

        int sent = 0;
        int failed = 0;
        int disabled = 0;
        int skipped = 0;
        List<Long> removable = new ArrayList<>();
        for (CompletableFuture<Sent> future : sending) {
            Sent result = future.join();
            switch (result.result()) {
                case SENT -> {
                    sent++;
                    // 끝난 여행은 보내고 나면 등록도 끝이다. 안 지우면 내일도 대상에 남아 같은 종료를 또 보낸다.
                    if (result.target().doneAfterSend()) {
                        removable.add(result.target().rowId());
                    }
                }
                case GONE -> removable.add(result.target().rowId());
                case THROTTLED -> skipped++;
                case FAILED -> failed++;
                case DISABLED -> disabled++;
            }
        }

        int removed = removeRows(removable);
        // 비활성(키 없음)은 실패와 따로 센다 — 고칠 곳이 코드가 아니라 배포 설정이라 대응이 다르다.
        if (disabled > 0) {
            log.warn("잠금화면 갱신이 비활성입니다 — APNs 설정이 없습니다 보내지못함={}건", disabled);
        }
        if (skipped > 0) {
            log.warn("APNs 속도 제한으로 이번 회차를 중단했습니다 남김={}건 — 다음 자정에 다시 대상이 됩니다", skipped);
        }
        // 조용히 0건인 상태를 아무도 모르면 안 된다. 정리 건수를 함께 남겨야 "죽은 토큰이 원래 많다"와
        // "설정이 틀려 전부 죽은 것으로 읽힌다"를 로그만으로 가른다.
        log.info("잠금화면 갱신 완료 대상={}건 성공={}건 실패={}건 정리={}건",
                targets.size(), sent, failed, removed);
        return sent;
    }

    /** 세마포어로 동시 발송 수를 묶는다 — 가상 스레드는 값싸지만 상대(APNs)는 그렇지 않다. */
    private Sent send(LiveActivityTarget target, Semaphore inFlight, AtomicBoolean throttled) {
        if (throttled.get()) {
            return new Sent(target, ApnsResult.THROTTLED);
        }
        inFlight.acquireUninterruptibly();
        try {
            ApnsResult result = liveActivitySender.send(target.token(), target.toPush());
            if (result == ApnsResult.THROTTLED) {
                throttled.set(true);
            }
            return new Sent(target, result);
        } finally {
            inFlight.release();
        }
    }

    /**
     * 쓸모없어진 등록을 지운다 — 계속 두면 매일 실패하거나 끝난 여행에 종료를 다시 보낸다.
     *
     * <p><b>한 건의 삭제 실패로 나머지를 버리지 않는다.</b> 이 정리는 곁가지고, 여기서 예외가 올라가면
     * 이미 성공한 발송의 집계까지 잃는다.
     */
    private int removeRows(List<Long> rowIds) {
        int removed = 0;
        for (Long rowId : rowIds) {
            try {
                removed += liveActivityTokenRepository.deleteById(rowId);
            } catch (RuntimeException e) {
                log.warn("잠금화면 등록을 지우지 못했습니다 rowId={} cause={}", rowId, e.getClass().getSimpleName());
            }
        }
        return removed;
    }

    /** 보낸 결과 — 행을 지울지 정하려면 어느 대상이었는지가 함께 필요하다. */
    private record Sent(LiveActivityTarget target, ApnsResult result) {
    }
}
