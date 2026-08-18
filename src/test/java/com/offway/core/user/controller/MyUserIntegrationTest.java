package com.offway.core.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.User;
import com.offway.core.user.repository.UserJpaRepository;
import com.offway.core.user.service.TokenIssuer;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 내 정보 조회의 HTTP 계약(#282).
 *
 * <p>앱이 마이페이지를 이 응답으로 그린다. 그래서 <b>어떤 필드가 null 로 올 수 있는지</b>가 계약의 핵심이다 —
 * 값이 없는 것과 빈 문자열을 구분하지 못하면 화면에 빈 줄이 그려진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyUserIntegrationTest {

    private static final String ME_URL = "/api/v1/users/me";
    private static final String CALLBACK_URL = "/api/v1/auth/callback/%s";
    private static final String BEARER = "Bearer ";

    @TestConfiguration
    static class SocialStubConfiguration {

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
    private UserJpaRepository userJpaRepository;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Test
    void 로그인한_사용자의_정보를_공통_래퍼로_내려준다() throws Exception {
        String accessToken = login(AuthProvider.GOOGLE, "세빈", "sevin@example.com");

        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.detail").value("요청이 정상 처리되었습니다."))
                .andExpect(jsonPath("$.data.nickname").value("세빈"))
                .andExpect(jsonPath("$.data.email").value("sevin@example.com"))
                .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.joinedAt").isNotEmpty());
    }

    @Test
    void 어느_provider_로_로그인했는지_그대로_준다() throws Exception {
        // 앱이 마이페이지에 "카카오로 로그인" 을 그린다. 값이 로그인 경로와 같은 이름이어야 헷갈리지 않는다.
        String accessToken = login(AuthProvider.APPLE, "세빈", null);

        mockMvc.perform(me(accessToken)).andExpect(jsonPath("$.data.provider").value("APPLE"));
    }

    @Test
    void 이메일이_없으면_필드는_실리고_값이_null_이다() throws Exception {
        // 빈 문자열로 채우면 앱이 "없다" 와 "빈 값" 을 구분할 수 없어 마이페이지에 빈 줄을 그린다.
        // 카카오는 동의를 안 하면, Apple 은 최초 로그인에서 앱이 안 넘겼으면 이 상태가 된다.
        String accessToken = login(AuthProvider.GOOGLE, "세빈", null);

        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                // doesNotExist() 를 쓰지 않는다. 그건 "non-null 값이 없다" 만 보므로 필드가 null 로 실린 것과
                // 아예 빠진 것을 구분하지 못한다. 이 응답은 null 로 나가는 것이 계약이고(MyUserResponse·UserApi
                // 둘 다 그렇게 적는다), 나중에 필드가 사라져도 doesNotExist() 는 그대로 통과한다.
                .andExpect(jsonPath("$.data.email").value(nullValue()))
                .andExpect(jsonPath("$.data.nickname").value("세빈"));
    }

    /**
     * 소셜 연결이 없는 계정은 {@code provider} 가 <b>null 로 실린다</b>.
     *
     * <p>local 개발 로그인(`/auth/dev-login`)이 만드는 상태다. 그 컨트롤러는 {@code @Profile("local")} 이라
     * 통합 테스트에서 부를 수 없어, 같은 상태(신원 없는 사용자)를 직접 만들어 확인한다.
     */
    @Test
    void 소셜_연결이_없는_계정은_provider가_null_로_실린다() throws Exception {
        User withoutIdentity = userJpaRepository.save(User.withNickname("연결없는사용자"));
        String accessToken = tokenIssuer.issueAccessToken(withoutIdentity.getId());

        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("연결없는사용자"))
                .andExpect(jsonPath("$.data.provider").value(nullValue()));
    }

    @Test
    void 닉네임이_안_왔으면_기본값이_채워져_있다() throws Exception {
        // nickname 은 화면이 반드시 쓰는 값이라 null 로 내리지 않는다 — 가입 때 기본값이 들어간다.
        String accessToken = login(AuthProvider.APPLE, null, null);

        mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").isNotEmpty());
    }

    @Test
    void 인증_없이_부르면_401_COMMON_401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    void 무효한_토큰이면_401_USER_004() throws Exception {
        // "자격증명이 없다"(COMMON-401)와 "토큰이 틀렸다"(USER-004)를 가른다 — 앱이 취할 행동이 다르다.
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, BEARER + "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-004"));
    }

    @Test
    void 탈퇴한_계정의_토큰이면_401_USER_006() throws Exception {
        // access 토큰은 무상태라 탈퇴 후에도 만료까지 서명 검증을 통과한다. 그 창에서 빈 응답이나 500 을
        // 주면 앱이 로그인 화면으로 돌아갈 신호를 못 받는다.
        String accessToken = login(AuthProvider.GOOGLE, "세빈", null);
        mockMvc.perform(delete(ME_URL).header(HttpHeaders.AUTHORIZATION, BEARER + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(me(accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-006"));
    }

    @Test
    void 남의_정보는_볼_수_없다() throws Exception {
        // 대상을 요청이 정하지 않는다 — 토큰이 정한다. 그래서 지목할 자리가 애초에 없다.
        String mine = login(AuthProvider.GOOGLE, "나", "me@example.com");
        String others = login(AuthProvider.GOOGLE, "남", "other@example.com");

        mockMvc.perform(me(mine)).andExpect(jsonPath("$.data.nickname").value("나"));
        mockMvc.perform(me(others)).andExpect(jsonPath("$.data.nickname").value("남"));
    }

    private MockHttpServletRequestBuilder me(String accessToken) {
        return get(ME_URL).header(HttpHeaders.AUTHORIZATION, BEARER + accessToken);
    }

    private String login(AuthProvider provider, String nickname, String email) throws Exception {
        socialIdentityVerifier.respondWith(provider, "sub-" + UUID.randomUUID(), nickname, email);
        String response = mockMvc.perform(post(CALLBACK_URL.formatted(provider.name().toLowerCase()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\": \"any-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }
}
