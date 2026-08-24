package com.offway.core.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.notification.domain.NotificationType;
import com.offway.core.notification.infrastructure.push.PushResult;
import com.offway.core.notification.infrastructure.push.PushSender;
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
 * <h2>알림 소유자는 UUID 인데 기기 소유 키는 문자열이다(#280)</h2>
 *
 * <p>알림·코스·연차의 소유는 {@code user_id}(UUID)로 옮겼지만 {@code device_push_token} 은 그 전환의
 * 범위 밖이라 소유 칸이 아직 {@code guest_id}(문자열)다. {@link PushDispatcher} 는 그 사이를
 * {@code userId.toString()} 으로 잇는다 — 그래서 이 테스트도 토큰을 <b>그 문자열</b>로 심는다.
 *
 * <p><b>지금 앱은 그 값을 적어 주지 않는다.</b> 기기 등록은 여전히 {@code X-Guest-Id} 헤더 값을 그 칸에
 * 넣으므로 운영에서는 이 조회가 0건이 된다. 그 상태를 꾸미지 않고
 * {@link #앱_게스트_키로_등록된_기기는_사용자_UUID로_찾지_못한다()} 가 있는 그대로 잠근다.
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
    void 등록된_기기가_없으면_보내지_않는다() {
        pushSender.respondWith(token -> {
            throw new AssertionError("보낼 기기가 없는데 발송을 시도했다");
        });

        assertEquals(0, pushDispatcher.dispatch(List.of(target(UUID.randomUUID(), 14L))));
    }

    /**
     * 앱이 심은 기기 등록은 <b>사용자 UUID 로 찾히지 않는다</b> — 지금 코드의 실제 동작이다.
     *
     * <p>{@link PushDispatcher} 는 알림 소유자를 {@code userId.toString()} 으로 바꿔
     * {@code device_push_token.guest_id} 를 뒤진다. 그런데 그 칸에 실제로 들어가는 값은 앱이
     * {@code X-Guest-Id} 헤더에 넣는 게스트 키라, 두 값은 <b>같아질 이유가 없다</b>. 그래서 알림은
     * 만들어지지만 푸시는 한 건도 나가지 않는다.
     *
     * <p><b>이 테스트는 그 상태가 옳다고 말하지 않는다.</b> 고리가 이어진 척 꾸미면 나중에 기기 소유 키를
     * 옮길 때 무엇이 바뀌는지 아무도 모른다 — 지금 동작을 못 박아 두면 그때 이 테스트가 먼저 빨간불이 된다.
     * 끊긴 고리 자체는 별도 이슈다({@code PushDispatcher.deliveries} 의 {@code withoutDevice} 경고가
     * 운영에서 이 상태를 드러낸다).
     */
    @Test
    void 앱_게스트_키로_등록된_기기는_사용자_UUID로_찾지_못한다() {
        UUID owner = UUID.randomUUID();
        String appGuestKey = "guest-" + UUID.randomUUID();
        String token = "token-270-unreachable-" + owner;
        registerToken(appGuestKey, token);
        pushSender.respondWith(t -> {
            throw new AssertionError("찾히지 않아야 할 기기로 발송을 시도했다");
        });

        assertEquals(0, pushDispatcher.dispatch(List.of(target(owner, 15L))), "사용자 UUID 로는 그 기기를 못 찾는다");
        // 못 찾았을 뿐이라 토큰은 그대로 남는다 — 죽은 토큰으로 오해해 지우면 안 된다.
        assertEquals(1, devicePushTokenRepository.findByOwner(appGuestKey).size());
    }

    /** 알림 소유자(UUID)를 기기 등록의 소유 키(문자열)로 맞춘다 — {@code PushDispatcher} 와 같은 규칙이다. */
    private static String deviceOwner(UUID userId) {
        return userId.toString();
    }

    private PushTarget target(UUID owner, Long courseId) {
        return new PushTarget(owner, NotificationType.TRIP_TOMORROW, courseId);
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

        void respondWith(Function<String, PushResult> behavior) {
            this.behavior = behavior;
            sent.clear();
        }

        Map<String, AtomicInteger> sentTokens() {
            return sent;
        }

        @Override
        public PushResult send(String token, NotificationType type, Long courseId) {
            sent.computeIfAbsent(token, key -> new AtomicInteger()).incrementAndGet();
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
