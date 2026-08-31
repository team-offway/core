package com.offway.core.user.service;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.OAuthState;
import com.offway.core.user.infrastructure.kakao.KakaoOAuthClient;
import com.offway.core.user.service.dto.IssuedToken;
import com.offway.core.user.service.dto.SocialLoginCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 브라우저 로그인 조율(#343) — 백오피스가 쓰는 유일한 로그인 경로다.
 *
 * <h2>앱 로그인과 무엇이 다른가</h2>
 *
 * <p><b>앞에 한 단계가 붙는 것이 전부다.</b> 앱은 SDK 가 액세스 토큰까지 만들어 우리에게 넘기지만,
 * 브라우저에는 SDK 가 없어 우리가 인가 코드를 토큰으로 바꿔야 한다. 그 뒤는 {@link AuthService#login}
 * 을 그대로 부른다.
 *
 * <pre>
 *   앱  :            [SDK 가 처리]        → 액세스 토큰 → AuthService.login
 *   웹  : 인가 → 코드 → 우리가 교환        → 액세스 토큰 → AuthService.login
 *                      ↑ 여기만 새로 붙는다
 * </pre>
 *
 * <p>그래서 <b>신원 확인 로직이 한 벌</b>로 남는다. 발급 앱 대조도, 회원번호 조회도, 어드민 화이트리스트
 * 대조도 앱과 같은 코드가 돈다 — 같은 사람이 앱으로 들어오든 백오피스로 들어오든 같은 회원번호가 나온다.
 * 웹용 신원 확인을 따로 만들었다면 두 벌이 어긋나는 날 <b>백오피스만 뚫리는</b> 구멍이 됐을 것이다.
 *
 * <p>{@code @Transactional} 이 없는 것도 {@link AuthService} 와 같은 이유다 — 외부 호출이 트랜잭션 안에
 * 들어가면 read-timeout 동안 DB 커넥션을 잡아 풀이 마른다. DB 작업은 {@code AuthService} 가 이미 별도
 * 빈에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebOAuthService {

    /** 콜백이 암호화된 통로로 돌아오는지 가리는 기준. 스킴만 보면 되므로 {@code URI} 파싱까지 가지 않는다. */
    private static final String HTTPS_SCHEME_PREFIX = "https://";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final AuthService authService;
    private final AuthProperties authProperties;

    /**
     * 웹 로그인을 받을 수 있는가.
     *
     * <p><b>예외가 아니라 boolean 이다.</b> 여기서 던지면 브라우저가 주소창 이동 중에 JSON 401 을 마주해
     * 화면이 죽는다. 호출자가 먼저 묻고 화면으로 되돌려 보낼 수 있게 판정만 돌려준다.
     */
    public boolean available() {
        return authProperties.kakaoWebLoginConfigured();
    }

    /** 사용자를 보낼 카카오 동의 화면 주소. */
    public String authorizationUri(OAuthState state) {
        return kakaoOAuthClient.authorizationUri(state);
    }

    /**
     * 콜백이 https 로 돌아오는가 — {@link OAuthState} 쿠키에 {@code Secure} 를 붙일지 정한다.
     *
     * <p><b>요청의 스킴을 보지 않는다.</b> 운영은 Caddy 가 TLS 를 끝내고 평문으로 넘겨 앱이 보는 요청은
     * http 다({@code X-Forwarded-Proto} 를 신뢰하도록 설정하지 않았다). 그걸 기준 삼으면 운영에서
     * {@code Secure} 가 빠진다.
     *
     * <p>대신 <b>콜백 주소 자체</b>를 본다. 쿠키가 살아남아야 하는 바로 그 주소라 어긋날 수가 없다.
     */
    public boolean secureCallback() {
        String redirectUri = authProperties.kakaoRedirectUri();
        return redirectUri != null && redirectUri.startsWith(HTTPS_SCHEME_PREFIX);
    }

    /**
     * 인가 코드로 로그인한다.
     *
     * <p><b>닉네임·이메일을 넘기지 않는다.</b> 그 값들은 Apple 이 최초 인증 응답에만 주기 때문에 앱이
     * 받아 넘기는 것이고, 카카오는 프로필 조회가 직접 답한다. 여기서 채울 것이 없다.
     */
    public IssuedToken login(String authorizationCode) {
        String accessToken = kakaoOAuthClient.exchange(authorizationCode);
        return authService.login(SocialLoginCommand.builder()
                .provider(AuthProvider.KAKAO)
                .credential(accessToken)
                .build());
    }
}
