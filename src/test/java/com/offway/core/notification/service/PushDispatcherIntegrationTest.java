package com.offway.core.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.notification.domain.Notification;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.infrastructure.push.PushMessage;
import com.offway.core.notification.infrastructure.push.PushResult;
import com.offway.core.notification.infrastructure.push.PushSender;
import com.offway.core.notification.repository.NotificationRepository;
import com.offway.core.notification.service.dto.PushTarget;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 알림을 기기로 내보내는 경로(#270).
 *
 * <p>외부 경계인 FCM 만 프로그래머블 stub 으로 격리한다 — 토큰 저장소는 내부라 실제 빈을 쓴다.
 *
 * <p>여기서 보는 것은 발송 자체가 아니라 <b>발송을 둘러싼 결정</b>이다: 같은 기기에 두 번 보내지 않는가,
 * 죽은 토큰을 실제로 걷어내는가, 일시 실패한 토큰은 남기는가.
 *
 * <h2>알림 소유자는 UUID 인데 기기 소유 칸은 문자열이다(#280)</h2>
 *
 * <p>알림·코스·연차의 소유는 {@code user_id}(UUID)로 옮겼고 기기 등록도 같은 사용자를 주인으로 삼는다.
 * 다만 {@code device_push_token} 의 소유 <b>칸 타입</b>은 아직 문자열({@code guest_id})이라
 * {@link PushDispatcher} 가 그 사이를 {@code userId.toString()} 으로 잇는다 — 그래서 이 테스트도
 * 토큰을 <b>그 문자열</b>로 심는다. 칸 이름 정리는 별도 작업이다.
 *
 * <p><b>잇는 규칙이 어긋나면 조회가 통째로 0건이 된다.</b> 등록과 발송이 같은 문자열을 쓰지 않으면
 * 알림은 만들어지는데 푸시만 조용히 안 간다 — 예외도 안 나므로 아무 흔적이 없다.
 * {@link #소유_문자열이_다르면_그_기기는_찾히지_않는다()} 가 그 규칙을 잠근다.
 */
@SpringBootTest
@Import(PushDispatcherIntegrationTest.StubPushSenderConfig.class)
class PushDispatcherIntegrationTest {

    @Autowired
    private PushDispatcher pushDispatcher;

    @Autowired
    private DevicePushTokenRepository devicePushTokenRepository;

    @Autowired
    private StubPushSender pushSender;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void 소유자의_모든_기기로_보낸다() {
        UUID owner = UUID.randomUUID();
        registerToken(deviceOwner(owner), "token-270-phone-" + owner);
        registerToken(deviceOwner(owner), "token-270-tablet-" + owner);
        pushSender.respondWith(token -> PushResult.SENT);

        int sent = pushDispatcher.dispatch(List.of(target(owner, 10L)));

        assertEquals(2, sent);
        assertEquals(2, pushSender.sentTokens().size());
    }

    @Test
    void 같은_토큰이_두_소유자로_있어도_한_번만_보낸다() {
        // 앱을 지웠다 깔면 기기 등록의 소유 키는 새로 나오지만 FCM 토큰은 이어질 수 있다(#264 가 유니크 키를
        // (소유자, 토큰) 복합으로 둔 대가). 그대로 두면 같은 기기에 같은 알림이 두 번 간다.
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        String sharedToken = "token-270-reinstalled-" + older;
        registerToken(deviceOwner(older), sharedToken);
        registerToken(deviceOwner(newer), sharedToken);
        pushSender.respondWith(token -> PushResult.SENT);

        int sent = pushDispatcher.dispatch(List.of(target(older, 11L), target(newer, 11L)));

        assertEquals(1, sent, "같은 기기에는 한 번만 간다");
    }

    @Test
    void 죽은_토큰은_지운다() {
        // FCM 이 UNREGISTERED 로 답한 토큰은 앱이 지워졌거나 재설치된 것이라 계속 두면 매번 실패한다.
        UUID owner = UUID.randomUUID();
        registerToken(deviceOwner(owner), "token-270-dead-" + owner);
        pushSender.respondWith(token -> PushResult.UNREGISTERED);

        pushDispatcher.dispatch(List.of(target(owner, 12L)));

        assertTrue(devicePushTokenRepository.findByOwner(deviceOwner(owner)).isEmpty(), "죽은 토큰은 남지 않는다");
    }

    @Test
    void 일시_실패한_토큰은_남긴다() {
        // 네트워크·서버 오류는 다음에 성공할 수 있다. 지우면 멀쩡한 기기가 알림을 영영 못 받는다.
        UUID owner = UUID.randomUUID();
        registerToken(deviceOwner(owner), "token-270-transient-" + owner);
        pushSender.respondWith(token -> PushResult.FAILED);

        int sent = pushDispatcher.dispatch(List.of(target(owner, 13L)));

        assertEquals(0, sent);
        assertEquals(
                1, devicePushTokenRepository.findByOwner(deviceOwner(owner)).size(), "일시 실패는 토큰을 지우지 않는다");
    }

    @Test
    void 어느_알림인지_함께_보낸다() {
        // 이 값이 없으면 배너를 눌러 들어온 앱이 무엇을 읽음 처리할지 모른다. 코스 id 로는 대신할 수 없다 —
        // 한 코스에 TRIP_TOMORROW·TRIP_AFTER 가 둘 다 달릴 수 있어 어느 쪽인지 갈리지 않는다.
        UUID owner = UUID.randomUUID();
        String token = "token-357-id-" + owner;
        registerToken(deviceOwner(owner), token);
        long notificationId = saveNotification(owner, NotificationType.TRIP_TOMORROW, 20L);
        pushSender.respondWith(t -> PushResult.SENT);

        pushDispatcher.dispatch(List.of(new PushTarget(owner, NotificationType.TRIP_TOMORROW, 20L, notificationId)));

        assertEquals(notificationId, pushSender.messageTo(token).notificationId());
    }

    @Test
    void 안_읽은_알림_개수를_배지로_싣는다() {
        // iOS 는 이 값을 받아야 앱 아이콘에 숫자를 그린다. 앱이 켜 둔 표시 옵션은 "오면 반영한다" 는
        // 뜻이지 값을 만들어 내지 않는다.
        UUID owner = UUID.randomUUID();
        String token = "token-357-badge-" + owner;
        registerToken(deviceOwner(owner), token);
        long notificationId = saveNotification(owner, NotificationType.TRIP_TOMORROW, 21L);
        saveNotification(owner, NotificationType.TRIP_AFTER, 21L);
        pushSender.respondWith(t -> PushResult.SENT);

        pushDispatcher.dispatch(List.of(new PushTarget(owner, NotificationType.TRIP_TOMORROW, 21L, notificationId)));

        assertEquals(2, pushSender.messageTo(token).badge(), "안 읽은 두 건이 배지로 나가야 한다");
    }

    @Test
    void 같은_사람의_기기들은_같은_배지를_받는다() {
        // 배지를 기기마다 세면 질의가 기기 수만큼 돈다. 값은 어차피 같으므로 사람 단위로 한 번만 센다 —
        // 두 기기가 다른 숫자를 받으면 그 전제가 깨진 것이다.
        UUID owner = UUID.randomUUID();
        String phone = "token-357-phone-" + owner;
        String tablet = "token-357-tablet-" + owner;
        registerToken(deviceOwner(owner), phone);
        registerToken(deviceOwner(owner), tablet);
        long notificationId = saveNotification(owner, NotificationType.TRIP_TOMORROW, 22L);
        pushSender.respondWith(t -> PushResult.SENT);

        pushDispatcher.dispatch(List.of(new PushTarget(owner, NotificationType.TRIP_TOMORROW, 22L, notificationId)));

        assertEquals(1, pushSender.messageTo(phone).badge());
        assertEquals(pushSender.messageTo(phone).badge(), pushSender.messageTo(tablet).badge());
    }

    @Test
    void 등록된_기기가_없으면_보내지_않는다() {
        pushSender.respondWith(token -> {
            throw new AssertionError("보낼 기기가 없는데 발송을 시도했다");
        });

        assertEquals(0, pushDispatcher.dispatch(List.of(target(UUID.randomUUID(), 14L))));
    }

    /**
     * 소유 문자열이 다른 기기 등록은 <b>사용자 UUID 로 찾히지 않는다</b> — 조회가 정확히 일치로만 매칭된다.
     *
     * <p>{@link PushDispatcher} 는 알림 소유자를 {@code userId.toString()} 으로 바꿔
     * {@code device_push_token.guest_id} 를 뒤진다. 등록이 같은 규칙으로 그 칸을 채우지 않으면 두 값은
     * 절대 만나지 않는다 — <b>알림은 만들어지는데 푸시만 한 건도 안 나간다.</b> 예외가 아니라 0건이라
     * 아무 흔적이 없는 것이 이 실패의 성질이다.
     *
     * <p>#280 이전에 {@code X-Guest-Id} 헤더 값으로 심긴 등록이 정확히 이 모양이었다. 지금은 등록도
     * 발송도 같은 사용자 UUID 를 쓰므로 새 등록에는 이 일이 안 생기지만, <b>규칙을 한쪽만 바꾸면 다시
     * 생긴다.</b> 그래서 옛 데이터가 아니라 그 규칙을 잠근다 —
     * {@code PushDispatcher.deliveries} 의 {@code withoutDevice} 경고가 운영에서 이 상태를 드러낸다.
     */
    @Test
    void 소유_문자열이_다르면_그_기기는_찾히지_않는다() {
        UUID owner = UUID.randomUUID();
        String mismatchedOwner = "guest-" + UUID.randomUUID();
        String token = "token-270-unreachable-" + owner;
        registerToken(mismatchedOwner, token);
        pushSender.respondWith(t -> {
            throw new AssertionError("찾히지 않아야 할 기기로 발송을 시도했다");
        });

        assertEquals(0, pushDispatcher.dispatch(List.of(target(owner, 15L))), "사용자 UUID 로는 그 기기를 못 찾는다");
        // 못 찾았을 뿐이라 토큰은 그대로 남는다 — 죽은 토큰으로 오해해 지우면 안 된다.
        assertEquals(1, devicePushTokenRepository.findByOwner(mismatchedOwner).size());
    }

    /** 알림 소유자(UUID)를 기기 등록의 소유 키(문자열)로 맞춘다 — {@code PushDispatcher} 와 같은 규칙이다. */
    private static String deviceOwner(UUID userId) {
        return userId.toString();
    }

    /** 발송 대상 하나 — 알림 id 는 저장 없이도 되는 시나리오라 코스 id 에서 만든다. */
    private PushTarget target(UUID owner, Long courseId) {
        return new PushTarget(owner, NotificationType.TRIP_TOMORROW, courseId, courseId);
    }

    /** 안 읽은 알림을 실제로 하나 남긴다 — 배지는 DB 를 세어 나오는 값이라 진짜 행이 있어야 한다. */
    private long saveNotification(UUID owner, NotificationType type, long courseId) {
        return notificationRepository
                .saveIfAbsent(Notification.builder()
                        .userId(owner)
                        .type(type)
                        .courseId(courseId)
                        .createdAt(LocalDateTime.now())
                        .build())
                .orElseThrow(() -> new AssertionError("알림을 만들지 못했다"));
    }

    private void registerToken(String deviceOwner, String token) {
        devicePushTokenRepository.register(
                DevicePushToken.register(deviceOwner, token, DevicePlatform.ANDROID, LocalDateTime.now()));
    }

    /** 외부(FCM) 경계 stub — 응답을 매 테스트가 람다로 갈아 끼운다. */
    static class StubPushSender implements PushSender {

        /** default 는 throw 다 — 명시 세팅을 빠뜨리면 즉시 깨져, 앞 테스트의 상태가 살아남는 함정을 막는다. */
        private volatile Function<String, PushResult> behavior = token -> {
            throw new IllegalStateException("이 테스트는 발송 결과를 정하지 않았습니다");
        };

        private final Map<String, AtomicInteger> sent = new ConcurrentHashMap<>();

        /** 토큰별로 <b>무엇을</b> 보냈는지 — 배지·알림 id 는 결과값이 아니라 실어 보낸 내용이라 여기서만 보인다. */
        private final Map<String, PushMessage> messages = new ConcurrentHashMap<>();

        void respondWith(Function<String, PushResult> behavior) {
            this.behavior = behavior;
            sent.clear();
            messages.clear();
        }

        Map<String, AtomicInteger> sentTokens() {
            return sent;
        }

        PushMessage messageTo(String token) {
            return messages.get(token);
        }

        @Override
        public PushResult send(String token, PushMessage message) {
            sent.computeIfAbsent(token, key -> new AtomicInteger()).incrementAndGet();
            messages.put(token, message);
            return behavior.apply(token);
        }
    }

    @TestConfiguration
    static class StubPushSenderConfig {

        @Bean
        @Primary
        StubPushSender stubPushSender() {
            return new StubPushSender();
        }
    }
}
