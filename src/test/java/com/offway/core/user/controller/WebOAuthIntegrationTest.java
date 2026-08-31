package com.offway.core.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.kakao.StubKakaoOAuthClient;
import com.offway.core.user.infrastructure.kakao.StubKakaoProfileClient;
import com.offway.core.user.repository.AdminAccountJpaRepository;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 백오피스 진입 — 화면은 열리고 데이터는 잠긴다(#343).
 *
 * <h2>여기서 잠그는 것</h2>
 *
 * <ol>
 *   <li><b>화면이 인증 없이 열린다.</b> 안 열리면 브라우저가 Basic 팝업부터 띄워 로그인이 두 번이 된다
 *   <li><b>왕복이 {@code state} 로 묶인다.</b> 헐거워지면 남이 만든 인가 코드로 어드민을 로그인시킬 수 있다
 *   <li><b>웹으로 받은 토큰이 백오피스 API 를 통과한다.</b> 이 한 줄이 "웹 로그인이 앱과 같은 길을 탄다"
 *       는 설계의 증거다 — 신원 확인을 따로 만들었다면 여기서 어긋난다
 * </ol>
 *
 * <p>실패 갈래는 화면으로 되돌리는 것이 계약이라, status 가 아니라 <b>{@code Location} 프래그먼트</b>를
 * 단언한다. 여기가 어긋나면 사람이 주소창에서 원시 JSON 을 마주한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebOAuthIntegrationTest {

    private static final String START_URL = "/api/v1/auth/oauth2/kakao";
    private static final String CALLBACK_URL = START_URL + "/callback";
    private static final String ADMIN_API_URL = "/api/v1/admin/curated-links";
    private static final String ADMIN_SCREEN_URL = "/admin/";

    private static final String STATE_COOKIE = "offway_oauth_state";
    private static final String ADMIN_HOME_PREFIX = "/admin/#";

    @TestConfiguration
    static class KakaoStubConfiguration {

        @Bean
        @Primary
        StubKakaoOAuthClient stubKakaoOAuthClient() {
            return new StubKakaoOAuthClient();
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
    private StubKakaoOAuthClient kakaoOAuthClient;

    @Autowired
    private StubKakaoProfileClient kakaoProfileClient;

    @Autowired
    private AdminAccountJpaRepository adminAccountJpaRepository;

    // ── 화면은 열린다 ──────────────────────────────────────────────────────

    @Test
    void 백오피스_화면은_토큰_없이_열린다() throws Exception {
        // 잠그면 브라우저가 Basic 팝업을 먼저 띄워, 팝업 한 번 + 소셜 로그인 한 번이 된다.
        mockMvc.perform(get(ADMIN_SCREEN_URL)).andExpect(status().isOk());
    }

    @Test
    void 화면_파일도_토큰_없이_열린다() throws Exception {
        // HTML 이 열려도 JS·CSS 가 막히면 화면이 깨진 채 뜬다.
        mockMvc.perform(get("/admin/app.js")).andExpect(status().isOk());
    }

    @Test
    void 백오피스_데이터는_토큰_없이_열리지_않는다() throws Exception {
        // 화면을 연 대가로 데이터까지 열리면 안 된다 — 이 목록에는 미공개 항목이 들어 있다.
        mockMvc.perform(get(ADMIN_API_URL)).andExpect(status().isUnauthorized());
    }

    // ── 왕복은 state 로 묶인다 ────────────────────────────────────────────

    @Test
    void 로그인을_시작하면_카카오로_보내고_state_를_쿠키에_남긴다() throws Exception {
        MvcResult result = mockMvc.perform(get(START_URL))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://kauth.kakao.com/")))
                .andReturn();

        Cookie state = result.getResponse().getCookie(STATE_COOKIE);
        assertNotNull(state, "state 쿠키가 없으면 콜백에서 대조할 값이 없다");
        assertTrue(state.isHttpOnly(), "화면 JS 가 읽을 이유가 없는 값이다");
        // Strict 면 카카오에서 넘어오는 사이트 간 이동에 쿠키가 안 실려 모든 로그인이 invalid_state 가 된다.
        assertEquals("Lax", state.getAttribute("SameSite"));
    }

    @Test
    void 쿠키_없이_콜백이_오면_거절한다() throws Exception {
        // 남이 만든 인가 코드를 어드민 브라우저에 먹이는 경로가 여기서 막힌다.
        assertFragment(
                mockMvc.perform(get(CALLBACK_URL).param("code", "someone-elses-code").param("state", "guessed"))
                        .andExpect(status().isFound())
                        .andReturn(),
                "error=invalid_state");
    }

    @Test
    void 쿠키와_다른_state_가_오면_거절한다() throws Exception {
        assertFragment(
                mockMvc.perform(get(CALLBACK_URL)
                                .cookie(new Cookie(STATE_COOKIE, "our-value"))
                                .param("code", "code")
                                .param("state", "other-value"))
                        .andExpect(status().isFound())
                        .andReturn(),
                "error=invalid_state");
    }

    @Test
    void 사용자가_동의를_취소하면_조용히_되돌린다() throws Exception {
        // 카카오는 취소하면 code 없이 error 만 실어 보낸다. 이것을 "코드 없음" 으로 부르면 사유가 어긋난다.
        assertFragment(
                mockMvc.perform(get(CALLBACK_URL)
                                .cookie(new Cookie(STATE_COOKIE, "our-value"))
                                .param("state", "our-value")
                                .param("error", "access_denied"))
                        .andExpect(status().isFound())
                        .andReturn(),
                "error=denied");
    }

    @Test
    void 카카오가_코드를_거절하면_사유를_전한다() throws Exception {
        kakaoOAuthClient.respond(code -> {
            throw UserException.invalidIdToken(null);
        });

        assertFragment(startAndCallback("expired-code"), "error=rejected");
    }

    @Test
    void 카카오를_못_부르면_다른_사유를_전한다() throws Exception {
        // 사람이 할 일이 다르다 — 이쪽은 기다리면 풀린다.
        kakaoOAuthClient.respond(code -> {
            throw UserException.oidcProviderUnavailable(null);
        });

        assertFragment(startAndCallback("code"), "error=unavailable");
    }

    // ── 웹으로 받은 토큰이 앱과 같은 길을 탄다 ──────────────────────────────

    @Test
    void 어드민_명단에_있으면_웹으로_받은_토큰이_백오피스를_통과한다() throws Exception {
        String kakaoUserId = "web-login-" + UUID.randomUUID();
        adminAccountJpaRepository.save(AdminAccount.builder()
                .provider(AuthProvider.KAKAO)
                .providerUserId(kakaoUserId)
                .label("테스트 어드민")
                .build());
        kakaoOAuthClient.respondWith("kakao-access-token");
        kakaoProfileClient.respondWith(kakaoUserId, "세빈", null);

        String accessToken = accessTokenFrom(startAndCallback("valid-code"));

        // 이 한 줄이 설계의 증거다 — 교환 뒤로는 앱 로그인과 같은 코드가 돌아 같은 회원번호가 나오고,
        // 그래서 화이트리스트가 그대로 맞는다.
        mockMvc.perform(get(ADMIN_API_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void 명단에_없으면_로그인은_되지만_백오피스는_막힌다() throws Exception {
        String kakaoUserId = "outsider-" + UUID.randomUUID();
        kakaoOAuthClient.respondWith("kakao-access-token");
        kakaoProfileClient.respondWith(kakaoUserId, "남", null);

        String accessToken = accessTokenFrom(startAndCallback("valid-code"));

        // 화면이 "권한 없음" 을 띄우고 사용자 ID 를 보여주는 근거가 이 403 이다.
        mockMvc.perform(get(ADMIN_API_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 콜백을_처리하고_나면_state_쿠키를_지운다() throws Exception {
        kakaoOAuthClient.respondWith("kakao-access-token");
        kakaoProfileClient.respondWith("used-" + UUID.randomUUID(), "세빈", null);

        Cookie state = startAndCallback("valid-code").getResponse().getCookie(STATE_COOKIE);

        assertNotNull(state);
        // 1회용 값이라 남겨 두면 다음 왕복이 옛 값을 물고 시작해 원인을 알기 어려운 실패가 된다.
        assertEquals(0, state.getMaxAge());
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    /** 시작 요청이 만든 state 를 그대로 물고 콜백까지 간다 — 브라우저가 하는 일과 같다. */
    private MvcResult startAndCallback(String code) throws Exception {
        Cookie state = mockMvc.perform(get(START_URL)).andReturn().getResponse().getCookie(STATE_COOKIE);
        assertNotNull(state, "로그인 시작이 state 쿠키를 남겨야 한다");

        return mockMvc.perform(get(CALLBACK_URL).cookie(state).param("code", code).param("state", state.getValue()))
                .andExpect(status().isFound())
                .andReturn();
    }

    private void assertFragment(MvcResult result, String expected) {
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertEquals(ADMIN_HOME_PREFIX + expected, location);
    }

    private String accessTokenFrom(MvcResult result) {
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        assertTrue(location.startsWith(ADMIN_HOME_PREFIX + "access_token="), "성공은 토큰을 프래그먼트로 전한다: " + location);
        String fragment = location.substring((ADMIN_HOME_PREFIX + "access_token=").length());
        return URLDecoder.decode(fragment.split("&")[0], StandardCharsets.UTF_8);
    }
}
