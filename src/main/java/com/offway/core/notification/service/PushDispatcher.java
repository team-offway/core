package com.offway.core.notification.service;

import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.notification.infrastructure.push.PushResult;
import com.offway.core.notification.infrastructure.push.PushSender;
import com.offway.core.notification.service.dto.PushTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 만들어진 알림을 그 소유자의 기기로 내보낸다(#270).
 *
 * <p><b>트랜잭션 밖에서 부른다.</b> 외부 호출이라 트랜잭션 안에 넣으면 DB 커넥션을 발송 시간만큼 잡아
 * 풀이 마른다(persistence-convention).
 */
@Slf4j
@Service
public class PushDispatcher {

    /**
     * 동시에 열어 둘 발송 수. 순차로 돌면 지연이 기기 수만큼 곱해지고, 무제한이면 FCM 쪽 속도 제한을
     * 스스로 부른다. 발송은 기기당 수백 ms 라 이 정도면 수천 대도 분 단위로 끝난다.
     */
    private static final int MAX_CONCURRENT_SENDS = 16;

    private final PushSender pushSender;
    private final DevicePushTokenRepository devicePushTokenRepository;

    /** 발송 전용 풀. 요청 처리 스레드를 쓰면 배치가 사용자 요청과 자원을 다툰다. */
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PushDispatcher(PushSender pushSender, DevicePushTokenRepository devicePushTokenRepository) {
        this.pushSender = pushSender;
        this.devicePushTokenRepository = devicePushTokenRepository;
    }

    /**
     * 대상들에게 푸시를 보내고, 죽은 토큰을 걷어낸다.
     *
     * <p><b>같은 토큰에는 한 번만 보낸다.</b> 앱을 지웠다 깔면 게스트 ID 는 새로 발급되지만 FCM 토큰은
     * 이어질 수 있어, 같은 기기가 여러 소유자로 등록돼 있을 수 있다(#264 가 유니크 키를 (소유자, 토큰)
     * 복합으로 둔 대가다). 그대로 두면 같은 기기에 같은 알림이 두 번 간다.
     *
     * @return 실제로 보낸 건수
     */
    public int dispatch(List<PushTarget> targets) {
        if (targets.isEmpty()) {
            return 0;
        }

        List<Delivery> deliveries = deliveries(targets);
        if (deliveries.isEmpty()) {
            log.info("푸시 발송 — 보낼 기기가 없습니다 대상={}건", targets.size());
            return 0;
        }

        Semaphore inFlight = new Semaphore(MAX_CONCURRENT_SENDS);
        List<CompletableFuture<Delivered>> sending = deliveries.stream()
                .map(delivery -> CompletableFuture.supplyAsync(() -> send(delivery, inFlight), sendExecutor))
                .toList();

        int sent = 0;
        int failed = 0;
        int disabled = 0;
        Set<String> dead = new HashSet<>();
        for (CompletableFuture<Delivered> future : sending) {
            Delivered delivered = future.join();
            switch (delivered.result()) {
                case SENT -> sent++;
                case UNREGISTERED -> dead.add(delivered.token());
                case FAILED -> failed++;
                case DISABLED -> disabled++;
            }
        }

        int removed = removeDeadTokens(dead);
        // 몇 건 보내 몇 건 실패했는지 남긴다. 조용히 0건이면 발송이 죽은 것을 아무도 모른다.
        // 비활성(키 없음)은 실패와 따로 센다 — 고칠 곳이 코드가 아니라 배포 설정이라 대응이 다르다.
        if (disabled > 0) {
            log.warn("푸시 발송이 비활성입니다 — 서비스 계정 키가 없습니다 보내지못함={}건", disabled);
        }
        log.info("푸시 발송 완료 대상={}건 기기={}대 성공={}건 실패={}건 죽은토큰정리={}건",
                targets.size(), deliveries.size(), sent, failed, removed);
        return sent;
    }

    /** 세마포어로 동시 발송 수를 묶는다 — 가상 스레드는 값싸지만 상대(FCM)는 그렇지 않다. */
    private Delivered send(Delivery delivery, Semaphore inFlight) {
        inFlight.acquireUninterruptibly();
        try {
            return new Delivered(
                    delivery.token(),
                    pushSender.send(delivery.token(), delivery.target().type(), delivery.target().courseId()));
        } finally {
            inFlight.release();
        }
    }

    /**
     * 보낼 (기기, 내용) 쌍을 만든다 — <b>토큰 기준으로 한 번만</b>.
     *
     * <p>같은 토큰이 여러 소유자로 있으면 가장 최근에 갱신된 등록만 남긴다. 그 등록이 그 기기를 실제로
     * 쓰고 있는 사람일 가능성이 가장 높다.
     */
    private List<Delivery> deliveries(List<PushTarget> targets) {
        List<Delivery> deliveries = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        for (PushTarget target : targets) {
            List<DevicePushToken> tokens = new ArrayList<>(devicePushTokenRepository.findByOwner(target.guestId()));
            tokens.sort(Comparator.comparing(DevicePushToken::getUpdatedAt).reversed());
            for (DevicePushToken token : tokens) {
                if (seenTokens.add(token.getToken())) {
                    deliveries.add(new Delivery(token.getToken(), target));
                }
            }
        }
        return deliveries;
    }

    /**
     * 죽은 토큰을 지운다 — 계속 두면 발송할 때마다 실패한다.
     *
     * <p>토큰으로 지운다(소유자 범위가 아니라). 재설치로 같은 토큰이 여러 소유자로 남아 있으면 그 행들이
     * 전부 죽은 것이므로 함께 걷어낸다.
     */
    private int removeDeadTokens(Set<String> dead) {
        if (dead.isEmpty()) {
            return 0;
        }
        return dead.stream().mapToInt(devicePushTokenRepository::deleteByToken).sum();
    }

    /** 한 기기에 보낼 한 건. */
    private record Delivery(String token, PushTarget target) {
    }

    /** 보낸 결과 — 죽은 토큰을 걷어내려면 어느 토큰이었는지가 함께 필요하다. */
    private record Delivered(String token, PushResult result) {
    }
}
