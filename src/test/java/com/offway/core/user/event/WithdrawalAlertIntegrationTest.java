package com.offway.core.user.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.common.notification.Notifier;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import com.offway.core.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 탈퇴 알림 — <b>줄어든 인원으로, 커밋 뒤에, 개인정보 없이</b>.
 *
 * <h2>왜 통합으로 보나</h2>
 *
 * <p>이 기능의 값어치는 "이벤트가 발행된다" 가 아니라 <b>"탈퇴 API 를 부르면 알림이 나간다"</b> 다. 그
 * 사이에 트랜잭션 경계와 {@code AFTER_COMMIT} 이 있고, 정작 틀릴 수 있는 자리가 거기다 —
 * {@code UserWithdrawn} 의 다른 리스너 넷은 같은 트랜잭션에서 동기로 돌기 때문에, 이 리스너만 다른
 * 규칙을 따른다는 것이 코드만 봐서는 지켜지는지 알 수 없다.
 *
 * <p>DB 격리는 롤백 대신 <b>테스트마다 고유한 provider 식별자</b>로 한다({@code SignupAlertIntegrationTest}
 * 와 같은 방식) — {@code @Transactional} 을 붙이면 커밋이 없어 {@code AFTER_COMMIT} 리스너가 아예 안
 * 돌고, 그러면 이 테스트가 통째로 헛돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WithdrawalAlertIntegrationTest {

    private static final String CALLBACK_URL = "/api/v1/auth/callback/google";
    private static final String WITHDRAW_URL = "/api/v1/users/me";
    private static final String BEARER = "Bearer ";

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubSocialIdentityVerifier socialIdentityVerifier;

    @Autowired
    private CapturingNotifier notifier;

    @Autowired
    private UserRepository userRepository;

    /** 로그인 결과 — 탈퇴는 토큰으로 대상을 정하고, 식별자는 알림에 안 실렸는지 볼 때만 쓴다. */
    private record Session(String accessToken, String userId) {}

    /** 로그인 한 번 — 신원이 매번 달라야 가입으로 잡힌다. */
    private Session login() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, "sub-" + UUID.randomUUID(), "세빈", null);
        String response = mockMvc.perform(post(CALLBACK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-id-token\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        return new Session(accessToken, userIdOf(accessToken));
    }

    /**
     * access 토큰의 {@code sub} 가 사용자 식별자다.
     *
     * <p>로그인 응답에는 식별자가 없다 — 앱이 쓸 이유가 없어 안 내린다. 토큰에서 꺼내는 이 방식은
     * {@code UserWithdrawalIntegrationTest} 가 이미 쓰는 것이다.
     */
    private static String userIdOf(String accessToken) {
        // 정규식으로 쪼개지 않는다 — 점 하나를 이스케이프하는 자리라 실수가 잘 나고, 여기서 보려는 것은
        // 토큰 파싱이 아니다. JWT 는 `헤더.페이로드.서명` 이라 두 점 사이가 페이로드다.
        int firstDot = accessToken.indexOf('.');
        int secondDot = accessToken.indexOf('.', firstDot + 1);
        String payload = new String(
                Base64.getUrlDecoder().decode(accessToken.substring(firstDot + 1, secondDot)),
                StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.sub");
    }

    private void withdraw(String accessToken) throws Exception {
        mockMvc.perform(delete(WITHDRAW_URL).header(HttpHeaders.AUTHORIZATION, BEARER + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void 탈퇴하면_알림이_나간다() throws Exception {
        Session session = login();
        notifier.drain();

        withdraw(session.accessToken());

        List<String> sent = notifier.drain();
        assertEquals(1, sent.size(), "탈퇴인데 알림이 안 나갔다: " + sent);
        assertTrue(sent.get(0).contains("탈퇴"), sent.get(0));
    }

    /**
     * <b>이 테스트가 이 기능의 본체다.</b>
     *
     * <p>{@code UserWithdrawn} 은 사용자 행을 <b>지우기 전에</b> 발행된다(도메인들이 먼저 자기 데이터를
     * 치워야 하므로). 그래서 인원을 세는 시점이 잘못되면 <b>탈퇴 전 인원</b>이 나간다.
     *
     * <p>모양만 보면("현재 N명") 그 회귀가 그대로 통과한다. 숫자까지 고정해야, 누가 이 리스너를
     * {@code BEFORE_COMMIT} 으로 바꾸거나 이벤트 발행을 삭제 뒤로 옮길 때 여기서 걸린다.
     */
    @Test
    void 알림에_탈퇴한_사람을_뺀_인원이_실린다() throws Exception {
        Session session = login();
        notifier.drain();
        long before = userRepository.count();

        withdraw(session.accessToken());

        String message = notifier.drain().get(0);
        assertTrue(message.contains("현재 " + (before - 1) + "명"),
                "인원이 탈퇴 전 수 - 1 이 아니다(탈퇴 전 인원을 세고 있다): " + message);
    }

    /**
     * <b>가입과 탈퇴가 짝지어 보인다.</b>
     *
     * <p>이 알림이 없던 동안 실제로 헷갈렸던 장면을 그대로 재현한다 — 운영에서 가입·탈퇴·가입이 이어졌고,
     * 탈퇴가 안 보여서 <b>"현재 13명" 두 줄</b>만 남았다. 알림이 고장난 것처럼 보였다.
     *
     * <p>이제 세 줄이 나가고 가운데 줄의 인원이 다르다. 그 차이가 "하나 들어오고 하나 나갔다" 를 말한다.
     */
    @Test
    void 가입과_탈퇴가_이어지면_인원이_오르고_내린_것이_보인다() throws Exception {
        notifier.drain();
        long before = userRepository.count();

        Session session = login();
        withdraw(session.accessToken());

        List<String> sent = notifier.drain();
        assertEquals(2, sent.size(), "가입·탈퇴 두 줄이 나와야 한다: " + sent);
        assertTrue(sent.get(0).contains("현재 " + (before + 1) + "명"), "가입 줄: " + sent.get(0));
        assertTrue(sent.get(1).contains("현재 " + before + "명"), "탈퇴 줄: " + sent.get(1));
    }

    /**
     * <b>개인을 특정할 값이 알림에 실리지 않는다.</b>
     *
     * <p>가입 알림이 닉네임·이메일을 뺀 것과 같은 기준이다(#592). 탈퇴는 {@code userId} 밖에 들 것이
     * 없는데, 그것도 싣지 않는다.
     */
    @Test
    void 알림에_사용자_식별자가_실리지_않는다() throws Exception {
        Session session = login();
        notifier.drain();

        withdraw(session.accessToken());

        String message = notifier.drain().get(0);
        assertFalse(message.contains(session.userId()), "사용자 식별자가 알림에 실렸다: " + message);
        // 조각이 새는 것까지 본다 — 앞 여덟 자만 실어도 그 사람을 특정할 수 있다.
        assertFalse(message.contains(session.userId().substring(0, 8)), "식별자 앞부분이 실렸다: " + message);
    }
}
