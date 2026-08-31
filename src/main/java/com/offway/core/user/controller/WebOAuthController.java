package com.offway.core.user.controller;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.logging.RootCause;
import com.offway.core.common.logging.SensitiveParams;
import com.offway.core.user.domain.OAuthState;
import com.offway.core.user.domain.WebLoginFailure;
import com.offway.core.user.service.WebOAuthService;
import com.offway.core.user.service.dto.IssuedToken;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저 로그인 진입(#343).
 *
 * <h2>왜 이 컨트롤러만 {@code ApiResponseBody} 를 안 쓰나</h2>
 *
 * <p>응답이 <b>3xx 리다이렉트</b>라서다. 브라우저는 본문이 아니라 {@code Location} 헤더를 소비하므로 래퍼를
 * 씌울 자리가 없다 — 응답 규약이 명시적으로 열어 둔 예외다(exception-and-response).
 *
 * <p>같은 이유로 <b>실패도 예외로 던지지 않는다.</b> 주소창이 이동하는 중이라 JSON 401 을 내리면 사람이
 * 원시 JSON 을 마주하고 화면이 죽는다. 사유를 {@link WebLoginFailure} 로 옮겨 백오피스 화면으로 되돌린다.
 * 조용히 넘기는 것이 아니라 <b>로그에는 남기고</b> 화면에도 사유를 전한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class WebOAuthController implements WebOAuthApi {

    /** 로그인 결과를 들고 돌아갈 화면. 정적 파일이라 앱 안에서 서빙된다. */
    private static final String ADMIN_HOME = "/admin/";

    /**
     * 결과를 <b>프래그먼트</b>로 전한다.
     *
     * <p>쿼리스트링(`?`)으로 넘기면 액세스 토큰이 서버 접근 로그·리퍼러 헤더에 그대로 박힌다. 프래그먼트는
     * 브라우저 밖으로 나가지 않아, 토큰이 우리 로그에도 남의 사이트에도 실리지 않는다.
     */
    private static final String FRAGMENT_SEPARATOR = "#";

    private static final String PARAM_SEPARATOR = "&";
    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private static final String EXPIRES_IN_PARAM = "expires_in";
    private static final String ERROR_PARAM = "error";

    /** {@link OAuthState} 를 실어 두는 쿠키. */
    private static final String STATE_COOKIE = "offway_oauth_state";

    /**
     * 쿠키 경로를 이 컨트롤러 아래로 좁힌다 — 다른 요청에 딸려 나갈 이유가 없는 값이다.
     *
     * <p>{@code @RequestMapping} 과 같은 값이라 한쪽만 고치면 어긋난다. 상수 하나로 묶어 둘 수 없는 것은
     * 어노테이션이 컴파일 상수만 받기 때문이고, 그래서 이 주석이 그 연결을 대신한다.
     */
    private static final String STATE_COOKIE_PATH = "/api/v1/auth/oauth2";

    /**
     * 쿠키 수명 — 동의 화면에 머무는 시간의 상한.
     *
     * <p>처음 로그인하는 사람은 카카오 계정 입력과 2단계 인증까지 하므로 5분으로는 모자랄 수 있다. 넘기면
     * {@code invalid_state} 로 떨어져 "왜 갑자기 안 되지" 가 된다. 그렇다고 길게 두면 1회용 값이 오래
     * 살아 있으므로, 넉넉하되 짧은 쪽으로 10분을 잡았다.
     */
    private static final Duration STATE_COOKIE_TTL = Duration.ofMinutes(10);

    /**
     * {@code SameSite=Lax} — <b>{@code Strict} 로 두면 동작하지 않는다.</b>
     *
     * <p>콜백은 카카오에서 우리 쪽으로 넘어오는 <b>사이트 간 이동</b>이라, {@code Strict} 면 브라우저가
     * 쿠키를 안 실어 보낸다. 그러면 우리가 방금 만든 값을 우리가 못 읽어 <b>모든 로그인이</b>
     * {@code invalid_state} 가 된다. {@code Lax} 는 최상위 GET 이동에 쿠키를 허용해 이 왕복을 잇는다.
     */
    private static final String SAME_SITE_LAX = "Lax";

    private final WebOAuthService webOAuthService;

    @Override
    @GetMapping("/kakao")
    public ResponseEntity<Void> start() {
        if (!webOAuthService.available()) {
            // 카카오로 보내고 나서 실패하면 사람이 동의까지 마친 뒤에야 막힌다. 시작 전에 끊는다.
            log.info("카카오 웹 로그인 설정이 없어 로그인을 시작하지 않는다");
            return redirectToAdmin(failureFragment(WebLoginFailure.NOT_CONFIGURED), expiredStateCookie());
        }
        OAuthState state = OAuthState.issue();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie(state).toString())
                .location(URI.create(webOAuthService.authorizationUri(state)))
                .build();
    }

    /**
     * 카카오가 되돌려 보낸 요청.
     *
     * <p><b>어느 갈래로 끝나든 state 쿠키를 지운다.</b> 1회용 값이라, 남겨 두면 다음 왕복이 옛 값을 물고
     * 시작해 원인을 알기 어려운 실패가 된다.
     */
    @Override
    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String stateCookie) {
        Optional<WebLoginFailure> rejection = reject(code, state, error, stateCookie);
        if (rejection.isPresent()) {
            return redirectToAdmin(failureFragment(rejection.get()), expiredStateCookie());
        }
        try {
            IssuedToken token = webOAuthService.login(code);
            return redirectToAdmin(successFragment(token), expiredStateCookie());
        } catch (BaseException exception) {
            // 사유는 이미 어댑터가 남겼다. 여기서는 "웹 로그인이 실패로 끝났다" 를 잇는다 —
            // 이 줄이 없으면 화면에 뜬 error 와 서버 로그를 연결할 고리가 없다.
            WebLoginFailure failure = WebLoginFailure.of(exception.errorCode().category());
            log.info("웹 로그인 실패 사유={} cause={}", failure.code(), RootCause.label(exception));
            return redirectToAdmin(failureFragment(failure), expiredStateCookie());
        }
    }

    /**
     * 로그인을 진행할 수 없는 이유 — 진행해도 되면 비어 있다.
     *
     * <p>순서가 규칙이다. <b>설정 → 사용자 취소 → state → 코드</b> 로 보는데, 앞의 것이 참이면 뒤를 물어야
     * 의미가 없기 때문이다. 예를 들어 사용자가 취소하면 코드가 아예 없으므로, 코드부터 보면 취소를
     * "코드 없음" 으로 잘못 부른다.
     */
    private Optional<WebLoginFailure> reject(String code, String state, String error, String stateCookie) {
        if (!webOAuthService.available()) {
            log.info("카카오 웹 로그인 설정이 없어 콜백을 처리하지 않는다");
            return Optional.of(WebLoginFailure.NOT_CONFIGURED);
        }
        if (error != null && !error.isBlank()) {
            // **카카오가 준 값이라고 믿지 않는다.** 이 경로는 누구나 직접 부를 수 있어, 이 파라미터는
            // 카카오가 정한 값 집합이 아니라 임의의 사용자 입력이다. 개행이 섞이면 로그 한 줄이 여러
            // 줄로 쪼개져 가짜 로그 줄을 지어낼 수 있다(CWE-117).
            log.info("카카오가 로그인을 되돌려보냈다 reason={}", SensitiveParams.forLog(error));
            return Optional.of(WebLoginFailure.DENIED);
        }
        if (stateCookie == null || stateCookie.isBlank() || !new OAuthState(stateCookie).matches(state)) {
            // 쿠키 만료인지 위조인지 구분하지 않는다 — 어느 쪽이든 할 일은 다시 시작하는 것 하나다.
            log.info("웹 로그인 state 불일치 — 우리가 시작한 로그인이 아니거나 쿠키가 만료됐다");
            return Optional.of(WebLoginFailure.INVALID_STATE);
        }
        if (code == null || code.isBlank()) {
            log.info("웹 로그인 콜백에 인가 코드가 없다");
            return Optional.of(WebLoginFailure.DENIED);
        }
        return Optional.empty();
    }

    /**
     * 성공 프래그먼트.
     *
     * <p><b>refresh 토큰은 싣지 않는다.</b> 수명이 60일이라 브라우저에 두면 잃었을 때의 대가가 크고,
     * 백오피스는 하루 몇 번 여는 화면이라 access 토큰이 만료되면 다시 누르는 편이 낫다. 카카오가 이미
     * 로그인돼 있어 그 왕복은 클릭 한 번이다.
     */
    private String successFragment(IssuedToken token) {
        return ACCESS_TOKEN_PARAM + "=" + encode(token.accessToken())
                + PARAM_SEPARATOR
                + EXPIRES_IN_PARAM + "=" + token.expiresInSeconds();
    }

    private String failureFragment(WebLoginFailure failure) {
        return ERROR_PARAM + "=" + encode(failure.code());
    }

    /**
     * 프래그먼트에 실을 값 인코딩.
     *
     * <p>JWT 는 base64url 이라 그대로 둬도 안전하지만, 인코딩을 값의 모양에 기대지 않는다 — 나중에 다른
     * 값을 실을 때 조용히 깨지는 자리다.
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ResponseEntity<Void> redirectToAdmin(String fragment, ResponseCookie cookie) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(ADMIN_HOME + FRAGMENT_SEPARATOR + fragment))
                .build();
    }

    private ResponseCookie stateCookie(OAuthState state) {
        return baseStateCookie(state.value()).maxAge(STATE_COOKIE_TTL).build();
    }

    /** 빈 값 + 수명 0 — 브라우저에게 지우라는 뜻이다. */
    private ResponseCookie expiredStateCookie() {
        return baseStateCookie("").maxAge(Duration.ZERO).build();
    }

    /**
     * {@code HttpOnly} 라 화면 JS 가 읽지 못한다. 읽을 이유가 없는 값이고, 못 읽게 해 두면 화면에 스크립트가
     * 끼어들어도 이 값으로 왕복을 위조할 수 없다.
     */
    private ResponseCookie.ResponseCookieBuilder baseStateCookie(String value) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(webOAuthService.secureCallback())
                .path(STATE_COOKIE_PATH)
                .sameSite(SAME_SITE_LAX);
    }
}
