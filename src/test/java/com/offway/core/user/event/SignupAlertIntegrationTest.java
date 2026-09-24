package com.offway.core.user.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.common.notification.Notifier;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.infrastructure.kakao.StubKakaoProfileClient;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 가입 알림(#610) — <b>가입만, 커밋 뒤에, 개인정보 없이</b>.
 *
 * <h2>왜 통합으로 보나</h2>
 *
 * <p>이 기능의 값어치는 "이벤트가 발행된다" 가 아니라 <b>"로그인 API 를 부르면 알림이 나간다"</b> 다.
 * 그 사이에 트랜잭션 경계와 {@code AFTER_COMMIT} 이 있어서, 단위 테스트로는 정작 틀릴 수 있는 자리를
 * 못 본다.
 *
 * <p>DB 격리는 롤백 대신 <b>테스트마다 고유한 provider 식별자</b>로 한다({@code AuthIntegrationTest} 와
 * 같은 방식) — {@code @Transactional} 을 붙이면 커밋이 없어 {@code AFTER_COMMIT} 리스너가 아예 안 돌고,
 * 그러면 이 테스트가 통째로 헛돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupAlertIntegrationTest {

    private static final String CALLBACK_URL = "/api/v1/auth/callback/%s";

    /** 보낸 문구를 모으는 대체 구현 — 디스코드는 외부 경계라 실제로 부르지 않는다. */
    static class CapturingNotifier implements Notifier {

        private final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }

        List<String> drain() {
            List<String> copy = new ArrayList<>(sent);
            sent.clear();
            return copy;
        }
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        CapturingNotifier capturingNotifier() {
            return new CapturingNotifier();
        }

        @Bean
        StubSocialIdentityVerifier stubSocialIdentityVerifier() {
            return new StubSocialIdentityVerifier();
        }

        @Bean
        @Primary
        StubKakaoProfileClient stubKakaoProfileClient() {
            return new StubKakaoProfileClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSocialIdentityVerifier socialIdentityVerifier;

    @Autowired
    private StubKakaoProfileClient kakaoProfileClient;

    @Autowired
    private CapturingNotifier notifier;

    private static String uniqueProviderUserId() {
        return "sub-" + UUID.randomUUID();
    }

    /**
     * 로그인 한 번 — provider 마다 세팅할 stub 이 다르다.
     *
     * <p><b>카카오는 프로필 API 를 한 번 더 탄다.</b> 다른 provider 는 ID 토큰 검증으로 신원이 끝나는데
     * 카카오는 액세스 토큰으로 프로필을 물어야 하고, 그 경계가 {@code StubKakaoProfileClient} 다.
     * 그걸 안 세우면 stub 의 default 가 throw 라 500 이 난다 — 실제로 여기서 한 번 걸렸다.
     */
    private void login(AuthProvider provider, String subject, String nickname, String email) throws Exception {
        if (provider == AuthProvider.KAKAO) {
            kakaoProfileClient.respondWith(subject, nickname, email);
        }
        socialIdentityVerifier.respondWith(provider, subject, nickname, email);
        mockMvc.perform(post(CALLBACK_URL.formatted(provider.name().toLowerCase()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-id-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 처음_로그인하면_가입_알림이_나간다() throws Exception {
        notifier.drain();

        login(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", "user@example.com");

        List<String> sent = notifier.drain();
        assertEquals(1, sent.size(), "가입인데 알림이 안 나갔다: " + sent);
        assertTrue(sent.get(0).contains("새 사용자"), sent.get(0));
        assertTrue(sent.get(0).contains("GOOGLE"), "어느 경로로 가입했는지 없다: " + sent.get(0));
    }

    /**
     * <b>재로그인에는 안 나간다.</b>
     *
     * <p>이게 없으면 심사자가 앱을 다시 열 때마다 알림이 울려, 정작 봐야 할 다른 알림이 묻힌다.
     */
    @Test
    void 같은_신원으로_다시_로그인하면_알리지_않는다() throws Exception {
        String subject = uniqueProviderUserId();
        login(AuthProvider.GOOGLE, subject, "세빈", null);
        notifier.drain();

        login(AuthProvider.GOOGLE, subject, "세빈", null);

        assertTrue(notifier.drain().isEmpty(), "재로그인에 알림이 나갔다");
    }

    /**
     * <b>개인을 특정할 값이 알림에 실리지 않는다.</b>
     *
     * <p>디스코드 채널에 남으면 그 자체가 유출 경로다. 여행지 평가를 익명으로 바꾼 판단(#592)과 같은
     * 기준이라, 문구가 바뀌어도 이 선은 지켜져야 한다.
     */
    @Test
    void 알림에_닉네임과_이메일이_실리지_않는다() throws Exception {
        notifier.drain();
        String nickname = "박세빈" + UUID.randomUUID();
        String email = "secret-" + UUID.randomUUID() + "@example.com";

        login(AuthProvider.KAKAO, uniqueProviderUserId(), nickname, email);

        String message = notifier.drain().get(0);
        assertFalse(message.contains(nickname), "닉네임이 알림에 실렸다: " + message);
        assertFalse(message.contains(email), "이메일이 알림에 실렸다: " + message);
        assertFalse(message.contains("@"), "이메일 흔적이 남았다: " + message);
    }

    /** 현재 인원을 함께 싣는다 — "몇 명째인가" 가 이 한 줄의 값어치를 만든다. */
    @Test
    void 알림에_현재_인원이_실린다() throws Exception {
        notifier.drain();

        login(AuthProvider.APPLE, uniqueProviderUserId(), "세빈", null);

        String message = notifier.drain().get(0);
        assertTrue(message.matches(".*현재 \\d+명.*"), "현재 인원이 없다: " + message);
    }
}
