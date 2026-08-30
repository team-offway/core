package com.offway.core.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.offway.core.common.exception.BaseException;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.RefreshToken;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.kakao.StubKakaoProfileClient;
import com.offway.core.user.infrastructure.social.StubSocialIdentityVerifier;
import com.offway.core.user.repository.UserJpaRepository;
import com.offway.core.user.service.AuthService;
import com.offway.core.user.service.TokenIssuer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** 동시 재발급 결과에서 실패를 표시하는 접두사. 성공은 새 refresh 토큰 원문이라 겹칠 수 없다. */
    private static final String FAILED = "FAILED:";

    private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

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

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void 다른_카카오_앱에서_발급된_액세스_토큰은_거부한다() throws Exception {
        // 카카오 프로필 조회(/v2/user/me)는 토큰이 유효하기만 하면 주인을 돌려준다 — 어느 앱이 발급했는지는
        // 알려주지 않는다. 앱 번호를 대조하지 않으면 남의 카카오 앱 토큰을 손에 넣은 사람이 그 토큰을 그대로
        // 우리 서버에 던져 그 사용자로 로그인할 수 있다. Apple·Google 에서 aud 가 막는 자리다.
        kakaoProfileClient.respondWith(uniqueProviderUserId(), "남의앱사용자", null);
        kakaoProfileClient.respondTokenInfoFromApp("9999999");

        mockMvc.perform(callback("kakao", "other-app-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    void 남의_앱_토큰으로는_가입도_일어나지_않는다() throws Exception {
        // 거부가 401 을 내는 것으로 끝나면 안 된다 — 그 사이 가입이 일어나 있으면 막은 의미가 없다.
        // 같은 신원으로 정상 로그인했을 때 isNewUser 가 true 면, 앞선 시도가 계정을 만들지 않았다는 뜻이다.
        String kakaoId = uniqueProviderUserId();
        kakaoProfileClient.respondWith(kakaoId, "사용자", null);
        kakaoProfileClient.respondTokenInfoFromApp("9999999");
        mockMvc.perform(callback("kakao", "other-app-access-token")).andExpect(status().isUnauthorized());

        kakaoProfileClient.respondWith(kakaoId, "사용자", null);

        mockMvc.perform(callback("kakao", "our-app-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void 카카오_토큰_정보_조회가_실패하면_502_USER_005() throws Exception {
        // 앱 번호를 확인하지 못한 것은 "확인했더니 남의 앱" 과 다르다 — 재시도로 풀릴 수 있어 502 로 구분한다.
        kakaoProfileClient.respondWith(uniqueProviderUserId(), "세빈", null);
        kakaoProfileClient.respondTokenInfo(accessToken -> {
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
        // 회전 직후 유예 창(정상 앱의 재시도·동시 요청)을 벗어나게 폐기 시각을 과거로 민다. 그러지 않으면
        // 이 재사용이 "재시도" 로 해석돼 탈취 경보가 울리지 않는다 — 그건 별도 테스트가 잠근다.
        backdateRevocation(issued.refreshToken());

        mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));

        mockMvc.perform(reissueRequest(rotated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));
    }

    // ── refresh 회전의 동시성 ──────────────────────────────────

    /**
     * 스레드를 <b>둘</b>로 짠다. 테스트 컨텍스트의 hikari {@code maximum-pool-size} 가 2 라 실효 동시성이 2 이고,
     * 스레드를 더 늘려도 커넥션을 기다리며 줄을 서 순차 실행에 가까워진다 — 숫자만 커지고 겹치지 않는다.
     */
    private static final int CONCURRENT_REISSUES = 2;

    @Test
    void 같은_refresh로_동시에_재발급하면_하나만_성공한다() throws Exception {
        // 잠금이 없으면 두 트랜잭션이 같은 스냅샷(revoked_at IS NULL)을 보고 둘 다 회전에 성공한다.
        // 토큰 하나에서 살아 있는 refresh 가 둘 나오고, 그 순간 재사용 감지의 보장이 성립하지 않는다.
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();

        List<String> results = reissueConcurrently(issued.refreshToken());

        assertEquals(1, results.stream().filter(result -> !result.startsWith(FAILED)).count(), results.toString());
    }

    @Test
    void 동시_재발급에서_진_요청이_세션을_끊지_않는다() throws Exception {
        // 진 요청을 탈취로 오인해 전체 폐기를 돌리면, 이긴 요청이 방금 받아 간 정상 토큰까지 죽어 사용자가
        // 멀쩡한 토큰을 들고도 로그아웃된다. 회전 직후 유예 창이 막는 자리다.
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();

        List<String> results = reissueConcurrently(issued.refreshToken());
        String winner = results.stream()
                .filter(result -> !result.startsWith(FAILED))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("동시 재발급에서 성공한 요청이 없다: " + results));

        mockMvc.perform(reissueRequest(winner)).andExpect(status().isOk());
    }

    @Test
    void 유예_창_안의_재시도는_경보를_울리지_않고_그_요청만_거절한다() throws Exception {
        // 네트워크 재시도로 같은 refresh 가 곧바로 다시 오는 것은 탈취가 아니다. 그 요청은 거절하되(줄 토큰이
        // 없다 — 새 토큰은 이미 이긴 요청이 가져갔다) 세션 전체를 끊지는 않는다.
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Tokens issued = login();
        String rotated = JsonPath.read(bodyOf(reissueRequest(issued.refreshToken())), "$.data.refreshToken");

        mockMvc.perform(reissueRequest(issued.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER-003"));

        mockMvc.perform(reissueRequest(rotated)).andExpect(status().isOk());
    }

    /** 같은 refresh 로 동시에 재발급을 시도하고, 성공은 새 refresh 를, 실패는 {@code FAILED:code} 를 돌려준다. */
    private List<String> reissueConcurrently(String refreshToken) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_REISSUES);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REISSUES);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REISSUES; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    try {
                        return authService.reissue(refreshToken).refreshToken();
                    } catch (BaseException exception) {
                        return FAILED + exception.errorCode().code();
                    }
                }));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
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

    /**
     * 폐기 시각을 유예 창 밖으로 민다 — "회전 직후 재시도" 가 아니라 "한참 뒤의 재사용" 을 만든다.
     *
     * <p><b>시계를 주입하지 않고 DB 를 직접 민다.</b> 유예 판정이 도메인이 받은 {@code now} 하나로 끝나
     * 흉내 낼 상태가 폐기 시각뿐이다. 프로덕션 코드에 테스트용 시계를 뚫는 것보다 싸다.
     *
     * <p><b>DB 안에서 상대적으로 뺀다.</b> 클라이언트에서 계산한 {@code Timestamp} 를 넣으면 드라이버가 JVM
     * 기본 시간대로 변환하는데, 이 커넥션은 {@code serverTimezone=Asia/Seoul} 이라 값이 그만큼 틀어진다 —
     * 과거로 민다는 것이 미래로 가서 "방금 폐기됨" 으로 읽혔다. 저장된 값에서 빼면 시간대가 끼어들지 않는다.
     */
    private void backdateRevocation(String refreshToken) {
        long seconds = RefreshToken.ROTATION_GRACE.plus(Duration.ofMinutes(1)).toSeconds();
        int updated = jdbcTemplate.update(
                "UPDATE refresh_token SET revoked_at = revoked_at - INTERVAL ? SECOND WHERE token_hash = ?",
                seconds,
                tokenIssuer.hashRefreshToken(refreshToken));
        assertEquals(1, updated, "폐기 시각을 밀 대상이 없다 — 앞선 회전이 실제로 폐기했는지 확인하라");
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

    /**
     * 로그인 성공은 <b>사용자 식별자가 로그에 나타나는 유일한 자리</b>다(#41).
     *
     * <p>로그인 요청은 아직 인증 전이라 다른 줄에는 {@code anon} 으로 찍힌다. 이 줄이 없으면 "이 사람이
     * 언제 들어왔나" 에 답할 수단이 아예 없다 — 계정 문의가 들어왔을 때 정작 필요한 것이 그것이다.
     *
     * <p>다른 줄은 앞 8자만 찍으므로, 그 앞자리로 이 줄을 찾아와 전체 값을 얻는 구조다. 그래서 여기서는
     * <b>전문</b>이어야 한다.
     */
    @Test
    void 로그인_성공은_사용자_식별자를_전문으로_남긴다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        String body;
        try {
            body = mockMvc.perform(callback("google", "any-id-token"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        } finally {
            logger.detachAppender(appender);
        }

        UUID userId = tokenIssuer.parseAccessToken(JsonPath.read(body, "$.data.accessToken")).userId();
        String line = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("로그인 성공"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("로그인 성공 로그가 없습니다"));
        assertTrue(line.contains("userId=" + userId), line);
        assertTrue(line.contains("신규가입=true"), line);
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

    // ── 동시 가입 ─────────────────────────────────────────────

    /**
     * 같은 신원으로 동시에 로그인해도 <b>둘 다 성공하고 계정은 하나다</b>.
     *
     * <p>로그인 버튼 더블탭이면 그대로 일어난다. 두 요청이 모두 "계정이 없다" 를 읽고 둘 다 만들려 해서 하나가
     * {@code uk_user_identity_provider} 에 걸리는데, 진 쪽을 그냥 실패시키면 사용자가 원한 상태(계정 하나)는
     * 이미 이뤄져 있는데도 로그인에 실패한다.
     *
     * <p><b>진 쪽은 먼저 만들어진 계정으로 들어온다</b> — 계정이 둘로 갈리면 "내 코스"·연차가 어느 쪽에
     * 붙었는지에 따라 사라진 것처럼 보인다.
     */
    @Test
    void 같은_신원으로_동시에_로그인해도_둘_다_성공하고_계정은_하나다() throws Exception {
        socialIdentityVerifier.respondWith(AuthProvider.GOOGLE, uniqueProviderUserId(), "세빈", null);
        long before = userJpaRepository.count();
        int attempts = 2;
        CyclicBarrier barrier = new CyclicBarrier(attempts);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    barrier.await();
                    return mockMvc.perform(callback("google", "any-id-token"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(
                        200,
                        result.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "동시 로그인 중 하나가 실패했다");
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(before + 1, userJpaRepository.count(), "동시 로그인이 계정을 둘 만들었다");
    }
}
