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
        String owner = "guest-270-multi-device";
        registerToken(owner, "token-270-phone");
        registerToken(owner, "token-270-tablet");
        pushSender.respondWith(token -> PushResult.SENT);

        int sent = pushDispatcher.dispatch(List.of(target(owner, 10L)));

        assertEquals(2, sent);
        assertEquals(2, pushSender.sentTokens().size());
    }

    @Test
    void 같은_토큰이_두_소유자로_있어도_한_번만_보낸다() {
        // 앱을 지웠다 깔면 게스트 ID 는 새로 나오지만 FCM 토큰은 이어질 수 있다(#264 가 유니크 키를
        // (소유자, 토큰) 복합으로 둔 대가). 그대로 두면 같은 기기에 같은 알림이 두 번 간다.
        String sharedToken = "token-270-reinstalled";
        registerToken("guest-270-old", sharedToken);
        registerToken("guest-270-new", sharedToken);
        pushSender.respondWith(token -> PushResult.SENT);

        int sent = pushDispatcher.dispatch(
                List.of(target("guest-270-old", 11L), target("guest-270-new", 11L)));

        assertEquals(1, sent, "같은 기기에는 한 번만 간다");
    }

    @Test
    void 죽은_토큰은_지운다() {
        // FCM 이 UNREGISTERED 로 답한 토큰은 앱이 지워졌거나 재설치된 것이라 계속 두면 매번 실패한다.
        String owner = "guest-270-dead";
        registerToken(owner, "token-270-dead");
        pushSender.respondWith(token -> PushResult.UNREGISTERED);

        pushDispatcher.dispatch(List.of(target(owner, 12L)));

        assertTrue(devicePushTokenRepository.findByOwner(owner).isEmpty(), "죽은 토큰은 남지 않는다");
    }

    @Test
    void 일시_실패한_토큰은_남긴다() {
        // 네트워크·서버 오류는 다음에 성공할 수 있다. 지우면 멀쩡한 기기가 알림을 영영 못 받는다.
        String owner = "guest-270-transient";
        registerToken(owner, "token-270-transient");
        pushSender.respondWith(token -> PushResult.FAILED);

        int sent = pushDispatcher.dispatch(List.of(target(owner, 13L)));

        assertEquals(0, sent);
        assertEquals(1, devicePushTokenRepository.findByOwner(owner).size(), "일시 실패는 토큰을 지우지 않는다");
    }

    @Test
    void 등록된_기기가_없으면_보내지_않는다() {
        pushSender.respondWith(token -> {
            throw new AssertionError("보낼 기기가 없는데 발송을 시도했다");
        });

        assertEquals(0, pushDispatcher.dispatch(List.of(target("guest-270-no-device", 14L))));
    }

    private PushTarget target(String owner, Long courseId) {
        return new PushTarget(owner, NotificationType.TRIP_TOMORROW, courseId);
    }

    private void registerToken(String owner, String token) {
        devicePushTokenRepository.register(
                DevicePushToken.register(owner, token, DevicePlatform.ANDROID, LocalDateTime.now()));
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
