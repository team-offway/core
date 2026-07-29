package com.offway.core.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.oidc.StubOidcTokenVerifier;
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

// DB 격리: 롤백 대신 테스트마다 고유한 provider sub 을 써서 계정이 섞이지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REISSUE_URL = "/api/v1/auth/reissue";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String BEARER = "Bearer ";

    @TestConfiguration
    static class OidcStubConfiguration {

        @Bean
        @Primary
        StubOidcTokenVerifier stubOidcTokenVerifier() {
            return new StubOidcTokenVerifier();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubOidcTokenVerifier oidcTokenVerifier;

    @Autowired
    private UserJpaRepository userJpaRepository;

    /** 테스트마다 고유한 provider 신원 — 롤백 없이 이전 실행과 계정이 섞이지 않게. */
    private static String uniqueSubject() {
        return "sub-" + UUID.randomUUID();
    }

    @Test
    void 처음_로그인하면_가입되고_토큰_쌍을_받는다() throws Exception {
        oidcTokenVerifier.respondWith(AuthProvider.GOOGLE, uniqueSubject(), "세빈");

        mockMvc.perform(loginRequest("GOOGLE", "any-id-token", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void 같은_provider_신원으로_다시_로그인해도_사용자는_하나다() throws Exception {
        // 매칭 키는 sub 이다. 재로그인이 계정을 늘리면 "내 코스"·연차가 매번 초기화된다.
        oidcTokenVerifier.respondWith(AuthProvider.KAKAO, uniqueSubject(), "세빈");
        long before = userJpaRepository.count();

        mockMvc.perform(loginRequest("KAKAO", "token-1", null)).andExpect(status().isOk());
        mockMvc.perform(loginRequest("KAKAO", "token-2", null)).andExpect(status().isOk());

        assertEquals(before + 1, userJpaRepository.count());
    }

    @Test
    void 애플은_ID토큰에_이름이_없어_요청_닉네임이_반영된다() throws Exception {
        oidcTokenVerifier.respondWith(AuthProvider.APPLE, uniqueSubject(), null);

        String response = mockMvc.perform(loginRequest("APPLE", "any-id-token", "세빈"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID userId = UUID.fromString(subjectOf(JsonPath.read(response, "$.data.accessToken")));
        assertEquals("세빈", userJpaRepository.findById(userId).orElseThrow().getNickname());
    }

    @Test
    void 발급받은_토큰으로_보호된_엔드포인트를_호출할_수_있다() throws Exception {
        oidcTokenVerifier.respondWith(AuthProvider.GOOGLE, uniqueSubject(), "세빈");
        Tokens issued = login("GOOGLE");

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + issued.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 토큰_없이_보호된_엔드포인트를_부르면_401이고_응답이_공통_래퍼다() throws Exception {
        // 이 401 은 서블릿 필터 단계라 GlobalExceptionHandler 가 닿지 못한다.
        // 전용 EntryPoint 가 없으면 body 가 비거나 HTML 로 나가 FE 가 매핑할 code 자체가 사라진다.
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("USER-004"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 무효한_access_토큰도_401_USER_004로_같은_모양이다() throws Exception {
        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + "not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-004"));
    }

    @Test
    void ID토큰_검증에_실패하면_401_USER_001() throws Exception {
        oidcTokenVerifier.respond((provider, idToken) -> {
            throw UserException.invalidIdToken(new IllegalStateException("서명 불일치"));
        });

        mockMvc.perform(loginRequest("GOOGLE", "tampered-token", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    void refresh를_쓰면_새_토큰_쌍이_나오고_기존_refresh는_바뀐다() throws Exception {
        oidcTokenVerifier.respondWith(AuthProvider.GOOGLE, uniqueSubject(), "세빈");
        Tokens issued = login("GOOGLE");

        String response = mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotEquals(issued.refreshToken(), JsonPath.read(response, "$.data.refreshToken"));
    }

    @Test
    void 이미_회전된_refresh를_재사용하면_401이고_사용자_토큰이_전부_폐기된다() throws Exception {
        // 정상 클라이언트는 회전된 토큰을 다시 쓰지 않는다 → 탈취 정황이라 살아 있는 토큰까지 끊는다.
        oidcTokenVerifier.respondWith(AuthProvider.GOOGLE, uniqueSubject(), "세빈");
        Tokens issued = login("GOOGLE");
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
        oidcTokenVerifier.respondWith(AuthProvider.GOOGLE, uniqueSubject(), "세빈");
        Tokens issued = login("GOOGLE");

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, BEARER + issued.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));
    }

    private record Tokens(String accessToken, String refreshToken) {}

    private Tokens login(String provider) throws Exception {
        String response = bodyOf(loginRequest(provider, "any-id-token", null));
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

    private static MockHttpServletRequestBuilder loginRequest(String provider, String idToken, String nickname) {
        String nicknameField = nickname == null ? "" : ", \"nickname\": \"%s\"".formatted(nickname);
        return post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\": \"%s\", \"idToken\": \"%s\"%s}".formatted(provider, idToken, nicknameField));
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
