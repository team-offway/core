package com.offway.core.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.kakao.StubKakaoProfileClient;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import com.offway.core.user.repository.UserJpaRepository;
import java.util.Base64;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// DB 격리: 롤백 대신 테스트마다 고유한 provider 식별자를 써서 계정이 섞이지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String CALLBACK_URL = "/api/v1/auth/callback/%s";
    private static final String REISSUE_URL = "/api/v1/auth/reissue";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String BEARER = "Bearer ";

    @TestConfiguration
    static class SocialStubConfiguration {

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
    private UserJpaRepository userJpaRepository;

    /** 테스트마다 고유한 provider 신원 — 롤백 없이 이전 실행과 계정이 섞이지 않게. */
    private static String uniqueProviderUserId() {
        return "sub-" + UUID.randomUUID();
    }

    // ── 로그인 계약 ────────────────────────────────────────────

    @Test
    void 처음_로그인하면_가입되고_isNewUser가_true다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", "user@example.com");

        mockMvc.perform(callback("google", "any-id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                // 필드 이름이 isNewUser 여야 한다. record 접근자를 bean 규약으로 읽으면 newUser 가 되는데,
                // 그러면 앱이 온보딩 분기를 못 한다 — 계약이라 이름까지 단언한다.
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void 같은_신원으로_다시_로그인하면_사용자는_하나고_isNewUser가_false다() throws Exception {
        // 매칭 키는 provider 식별자다. 재로그인이 계정을 늘리면 "내 코스"·연차가 매번 초기화된다.
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        long before = userJpaRepository.count();

        mockMvc.perform(callback("google", "token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true));
        mockMvc.perform(callback("google", "token-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        assertEquals(before + 1, userJpaRepository.count());
    }

    @Test
    void provider_경로값은_대소문자를_가리지_않는다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);

        mockMvc.perform(callback("GOOGLE", "any-id-token")).andExpect(status().isOk());
    }

    @Test
    void 지원하지_않는_provider_경로값은_400_USER_002() throws Exception {
        mockMvc.perform(callback("naver", "any-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER-002"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        mockMvc.perform(post(CALLBACK_URL.formatted("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    // ── Apple — 이름·이메일이 최초 로그인 요청에만 실린다 ──────────

    @Test
    void 애플은_토큰에_이름이_없어_요청_값이_반영된다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.APPLE, uniqueProviderUserId(), null, null);

        String response = bodyOf(callbackWithProfile("apple", "any-id-token", "홍길동", "user@example.com"));

        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        var saved = userJpaRepository.findById(userId).orElseThrow();
        assertEquals("홍길동", saved.getNickname());
        assertEquals("user@example.com", saved.getEmail());
    }

    @Test
    void 이름이_아무데서도_안_오면_기본_표시이름이_붙는다() throws Exception {
        // Apple 사용자가 이름 제공을 거부한 경우. 닉네임 하나 때문에 가입이 실패하면 안 된다.
        socialIdentityVerifier.respondWith(AuthProvider.APPLE, uniqueProviderUserId(), null, null);

        String response = bodyOf(callback("apple", "any-id-token"));

        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        assertEquals("여행자", userJpaRepository.findById(userId).orElseThrow().getNickname());
    }

    @Test
    void 토큰_검증에_실패하면_401_USER_001() throws Exception {
        // 서명 불일치·만료·aud 불일치가 전부 여기로 모인다 — 앱은 재로그인으로 반응한다.
        socialIdentityVerifier.respond((provider, credential) -> {
            throw UserException.invalidIdToken(new IllegalStateException("audience 불일치"));
        });

        mockMvc.perform(callback("google", "someone-elses-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    void provider_공개키를_못_가져오면_502_USER_005() throws Exception {
        // "네 토큰이 틀렸다"와 "구글이 안 뜬다"는 앱이 취할 행동이 정반대라 code 를 나눈다.
        socialIdentityVerifier.respond((provider, credential) -> {
            throw UserException.oidcProviderUnavailable(new IllegalStateException("JWKS 조회 실패"));
        });

        mockMvc.perform(callback("google", "any-id-token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("USER-005"));
    }

    // ── Kakao — 유일하게 로그인 경로에 외부 호출이 낀다 ────────────

    @Test
    void 카카오는_프로필_조회_결과로_가입된다() throws Exception {
        String kakaoId = uniqueProviderUserId();
        kakaoProfileClient.respondWith(kakaoId, "카카오세빈", "kakao@example.com");

        String response = bodyOf(callback("kakao", "kakao-access-token"));

        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        var saved = userJpaRepository.findById(userId).orElseThrow();
        assertEquals("카카오세빈", saved.getNickname());
        assertEquals("kakao@example.com", saved.getEmail());
    }

    @Test
    void 카카오가_동의를_거부해_닉네임_이메일이_없어도_가입된다() throws Exception {
        // 카카오는 프로필·이메일 동의를 각각 거부할 수 있다. 그때도 회원번호만 있으면 로그인은 성립한다.
        kakaoProfileClient.respondWith(uniqueProviderUserId(), null, null);

        String response = bodyOf(callback("kakao", "kakao-access-token"));

        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        var saved = userJpaRepository.findById(userId).orElseThrow();
        assertEquals("여행자", saved.getNickname());
        assertEquals(null, saved.getEmail());
    }

    @Test
    void 카카오가_액세스_토큰을_거부하면_401_USER_001() throws Exception {
        kakaoProfileClient.respond(accessToken -> {
            throw UserException.invalidIdToken(new IllegalStateException("카카오 401"));
        });

        mockMvc.perform(callback("kakao", "expired-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    void 카카오_프로필_API_가_죽으면_502_USER_005() throws Exception {
        // 타임아웃·5xx — 재시도로 풀릴 수 있으므로 401 과 구분해 내린다.
        kakaoProfileClient.respond(accessToken -> {
            throw UserException.oidcProviderUnavailable(new IllegalStateException("read timeout"));
        });

        mockMvc.perform(callback("kakao", "kakao-access-token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("USER-005"));
    }

    @Test
    void 클라이언트가_보낸_providerUserId는_신원_판단에_쓰이지_않는다() throws Exception {
        // 이 값을 믿으면 남의 식별자를 적어 그 계정으로 로그인할 수 있다 — 요청 한 번짜리 계정 탈취다.
        String verified = uniqueProviderUserId();
        kakaoProfileClient.respondWith(verified, "진짜사용자", null);

        String response = mockMvc.perform(post(CALLBACK_URL.formatted("kakao"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"accessToken": "kakao-access-token", "providerUserId": "victim-account-id"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 발급된 토큰이 가리키는 사용자는 요청이 사칭한 쪽이 아니라 카카오가 확인해 준 쪽이어야 한다.
        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        assertEquals("진짜사용자", userJpaRepository.findById(userId).orElseThrow().getNickname());
    }

    // ── 세션 수명 ─────────────────────────────────────────────

    @Test
    void 발급받은_토큰으로_보호된_엔드포인트를_호출할_수_있다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + issued.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 자격증명_없이_보호된_엔드포인트를_부르면_401이고_응답이_공통_래퍼다() throws Exception {
        // 이 401 은 서블릿 필터 단계라 GlobalExceptionHandler 가 닿지 못한다.
        // 전용 EntryPoint 가 없으면 body 가 비거나 HTML 로 나가 FE 가 매핑할 code 자체가 사라진다.
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 무효한_access_토큰은_401_USER_004로_재발급을_유도한다() throws Exception {
        // 아무것도 안 들고 온 요청(COMMON-401)과 다른 code 여야 한다 — 앱이 취할 행동이 다르다.
        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + "not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-004"));
    }

    @Test
    void refresh를_쓰면_새_토큰_쌍이_나오고_기존_refresh는_바뀐다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();

        String response = mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                // 재발급은 가입일 수 없다 — 여기서 true 가 나가면 앱이 매 시간 온보딩을 띄운다.
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotEquals(issued.refreshToken(), JsonPath.read(response, "$.data.refreshToken"));
    }

    @Test
    void 이미_회전된_refresh를_재사용하면_401이고_사용자_토큰이_전부_폐기된다() throws Exception {
        // 정상 클라이언트는 회전된 토큰을 다시 쓰지 않는다 → 탈취 정황이라 살아 있는 토큰까지 끊는다.
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();
        String rotated = JsonPath.read(bodyOf(reissueRequest(issued.refreshToken())), "$.data.refreshToken");

        mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));

        mockMvc.perform(reissueRequest(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));
    }

    @Test
    void 로그아웃하면_refresh가_폐기된다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + issued.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));
    }

    private record Tokens(String accessToken, String refreshToken) {}

    private Tokens login() throws Exception {
        String response = bodyOf(callback("google", "any-id-token"));
        return new Tokens(
                JsonPath.read(response, "$.data.accessToken"), JsonPath.read(response, "$.data.refreshToken"));
    }

    private String bodyOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static MockHttpServletRequestBuilder callback(String provider, String accessToken) {
        return post(CALLBACK_URL.formatted(provider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessToken\": \"%s\"}".formatted(accessToken));
    }

    private static MockHttpServletRequestBuilder callbackWithProfile(
            String provider, String accessToken, String name, String email) {
        return post(CALLBACK_URL.formatted(provider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessToken\": \"%s\", \"name\": \"%s\", \"email\": \"%s\"}"
                        .formatted(accessToken, name, email));
    }

    private static MockHttpServletRequestBuilder reissueRequest(String refreshToken) {
        return post(REISSUE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken));
    }

    /** access 토큰(JWT) payload 에서 sub 을 꺼낸다 — 발급된 토큰이 어느 사용자 것인지 확인용. */
    private static String subjectOf(String accessToken) {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        return JsonPath.read(payload, "$.sub");
    }
}
