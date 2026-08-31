package com.offway.core.notification.service;

import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.notification.infrastructure.push.PushMessage;
import com.offway.core.notification.infrastructure.push.PushResult;
import com.offway.core.notification.infrastructure.push.PushSender;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.PushTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final NotificationRepository notificationRepository;

    /** 발송 전용 풀. 요청 처리 스레드를 쓰면 배치가 사용자 요청과 자원을 다툰다. */
    private final ExecutorService sendExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public PushDispatcher(
            PushSender pushSender,
            DevicePushTokenRepository devicePushTokenRepository,
            NotificationRepository notificationRepository) {
        this.pushSender = pushSender;
        this.devicePushTokenRepository = devicePushTokenRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 대상들에게 푸시를 보내고, 죽은 토큰을 걷어낸다.
     *
     * <p><b>같은 토큰에는 한 번만 보낸다.</b> 소유 키가 인증된 사용자라(#280) 같은 계정으로 다시 깔면
     * 등록이 하나로 합쳐지지만, <b>한 기기에 여러 계정이 로그인하면</b> FCM 토큰은 그대로인 채 소유자만
     * 달라 등록이 여러 개 남는다(#264 가 유니크 키를 (소유자, 토큰) 복합으로 둔 대가다). 한 알림의
     * 대상이 그 소유자들을 함께 담으면 같은 기기에 같은 알림이 두 번 간다.
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

        Map<UUID, Integer> badges = badges(targets);
        Semaphore inFlight = new Semaphore(MAX_CONCURRENT_SENDS);
        List<CompletableFuture<Delivered>> sending = deliveries.stream()
                .map(delivery -> CompletableFuture.supplyAsync(() -> send(delivery, badges, inFlight), sendExecutor))
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
    private Delivered send(Delivery delivery, Map<UUID, Integer> badges, Semaphore inFlight) {
        inFlight.acquireUninterruptibly();
        try {
            PushTarget target = delivery.target();
            PushMessage message = new PushMessage(
                    target.type(), target.courseId(), target.notificationId(), badges.get(target.userId()));
            return new Delivered(delivery.token(), pushSender.send(delivery.token(), message));
        } finally {
            inFlight.release();
        }
    }

    /**
     * 사람마다 안 읽은 알림 개수를 <b>한 번만</b> 센다(#357). iOS 는 이 값을 받아야 앱 아이콘에 숫자를 그린다.
     *
     * <p>보낼 때 세면 안 된다. 발송은 기기마다 도는 팬아웃이라 <b>기기 수만큼</b> 질의가 돌고, 한 사람에게
     * 여러 알림이 함께 나가는 하루치 배치에서는 그만큼 또 곱해진다. 값은 어차피 같으므로 사람 단위로 한 번만
     * 세어 나눠 쓴다.
     *
     * <p><b>세는 데 실패해도 발송은 나간다.</b> 배지는 곁가지고, 여기서 예외가 올라가면 알림 자체가 안 간다 —
     * 숫자 하나 때문에 알림을 통째로 잃는 것은 바꾸려던 것과 정반대다. 그때는 키가 없어 배지가 안 실리고,
     * 앱은 직전 값을 그대로 둔다.
     */
    private Map<UUID, Integer> badges(List<PushTarget> targets) {
        Map<UUID, Integer> badges = new HashMap<>();
        for (UUID userId : targets.stream().map(PushTarget::userId).distinct().toList()) {
            try {
                badges.put(userId, Math.toIntExact(notificationRepository.countUnread(userId)));
            } catch (RuntimeException e) {
                // 왜 배지가 안 실렸는지 남긴다 — 폴백이 정상처럼 보이면 숫자가 안 뜨는 것을 아무도 모른다.
                log.warn("안 읽은 알림 수를 세지 못해 배지 없이 보냅니다 cause={}", e.getClass().getSimpleName());
            }
        }
        return badges;
    }

    /**
     * 보낼 (기기, 내용) 쌍을 만든다 — <b>토큰 기준으로 한 번만</b>.
     *
     * <p>같은 토큰이 여러 소유자로 있으면 가장 최근에 갱신된 등록만 남긴다. 그 등록이 그 기기를 실제로
     * 쓰고 있는 사람일 가능성이 가장 높다.
     *
     * <p><b>등록된 기기가 하나도 없는 대상을 센다.</b> 알림은 만들어졌는데 푸시가 안 나간 상태는 로그가
     * 없으면 아무 흔적을 남기지 않는다 — 앱이 토큰을 안 보낸 것인지, 소유 키가 어긋나 못 찾은 것인지를
     * 가르려면 이 숫자가 먼저 있어야 한다.
     */
    private List<Delivery> deliveries(List<PushTarget> targets) {
        List<Delivery> deliveries = new ArrayList<>();
        Set<String> seenTokens = new HashSet<>();
        int withoutDevice = 0;
        for (PushTarget target : targets) {
            List<DevicePushToken> tokens = new ArrayList<>(devicePushTokenRepository.findByOwner(deviceOwner(target)));
            if (tokens.isEmpty()) {
                withoutDevice++;
                continue;
            }
            tokens.sort(Comparator.comparing(DevicePushToken::getUpdatedAt).reversed());
            for (DevicePushToken token : tokens) {
                if (seenTokens.add(token.getToken())) {
                    deliveries.add(new Delivery(token.getToken(), target));
                }
            }
        }
        if (withoutDevice > 0) {
            log.warn("푸시를 보낼 기기가 없는 대상이 있습니다 — 알림은 목록에만 남습니다 대상={}건", withoutDevice);
        }
        return deliveries;
    }

    /**
     * 알림 소유자(인증된 {@code UUID})를 <b>기기 등록의 소유 키(문자열)</b>로 맞춘다.
     *
     * <p>알림·코스·연차의 소유는 {@code user_id} 로 옮겼지만 {@code device_push_token} 은 그 범위 밖이다
     * (#280) — 거기서 대상은 사람이 아니라 <b>기기</b>라 소유 키가 아직 문자열 칸({@code guest_id})이다.
     * 두 표현이 만나는 자리가 여기뿐이라 변환을 한곳에 모아 둔다.
     *
     * <p><b>기기 등록도 같은 값을 적는다.</b> {@code DeviceController} 가 principal 을 문자열로 넘겨
     * 저장하므로 이 대응이 성립한다. 등록이 요청 헤더 값을 넣던 시절에는 조회가 0건이 됐고, 그러면
     * 알림은 만들어지는데 푸시만 조용히 안 갔다 — {@code withoutDevice} 경고가 그 신호다.
     */
    private static String deviceOwner(PushTarget target) {
        return target.userId().toString();
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
