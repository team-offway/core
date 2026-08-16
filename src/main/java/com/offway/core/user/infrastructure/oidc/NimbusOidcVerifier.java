package com.offway.core.user.infrastructure.oidc;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityVerifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestOperations;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 서명된 ID 토큰(Apple · Google)을 provider 공개키(JWKS)로 검증하는 어댑터.
 *
 * <p><b>요청 경로에 외부 호출이 없다.</b> provider 별 {@link JwtDecoder} 를 만들어 캐시하고, 디코더가 JWKS 를 내부
 * 캐시하므로 매 로그인마다 provider 를 부르지 않는다. 키가 회전됐을 때만(kid 불일치) 다시 가져온다.
 *
 * <p>새 의존성이 없다 — 이미 있던 {@code spring-security-oauth2-jose}(Nimbus)가 JWKS 검증을 제공한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NimbusOidcVerifier implements SocialIdentityVerifier {

    /** JWKS 조회 상한 — 로그인 경로라 오래 물릴 수 없다. 기본값(30초)을 그대로 두지 않는 이유는 jwksClient() 주석에. */
    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(3);


    private static final String AUDIENCE_MISMATCH = "audience 가 일치하지 않습니다.";

    private static final String ISSUER_MISMATCH = "issuer 가 일치하지 않습니다.";

    private final AuthProperties authProperties;
    private final Map<AuthProvider, JwtDecoder> decoders = new ConcurrentHashMap<>();

    /** 서명된 ID 토큰을 주는 provider 전부를 맡는다 — 검증 절차가 완전히 같다. */
    @Override
    public boolean supports(AuthProvider provider) {
        return provider.oidc().isPresent();
    }

    @Override
    public SocialIdentity verify(AuthProvider provider, String credential) {
        AuthProvider.Oidc oidc =
                provider.oidc().orElseThrow(() -> new IllegalStateException("서명 검증 대상이 아닌 provider: " + provider));
        List<String> audiences = authProperties.audiencesOf(provider);
        // 설정이 비면 audience 검증이 무력화된다 — 남의 앱 토큰을 받아주느니 그 provider 를 닫는다.
        if (audiences.isEmpty()) {
            log.info("audience 가 설정되지 않은 provider 로그인 시도 provider={}", provider);
            throw UserException.unsupportedProvider();
        }
        Jwt jwt = decode(provider, oidc, credential, audiences);
        return new SocialIdentity(provider, jwt.getSubject(), nicknameOf(oidc, jwt), emailOf(jwt));
    }

    private Jwt decode(AuthProvider provider, AuthProvider.Oidc oidc, String idToken, List<String> audiences) {
        try {
            return decoders
                    .computeIfAbsent(provider, key -> buildDecoder(oidc, audiences))
                    .decode(idToken);
        } catch (BadJwtException exception) {
            // 서명 불일치·만료·형식 오류·클레임 검증 실패 — 클라이언트가 가진 토큰의 문제라 401.
            // 구체 사유는 남기지 않는다. 토큰 원문은 물론이고 "어디까지 맞았는지"도 공격자에게 줄 이유가 없다.
            log.info("ID 토큰 검증 실패 provider={}", provider);
            throw UserException.invalidIdToken(exception);
        } catch (JwtException exception) {
            // JWKS 조회 실패 등 provider 측 문제 — 재시도로 풀릴 수 있으므로 502 로 구분한다.
            log.warn("provider 공개키 조회 실패 provider={} cause={}", provider, exception.getClass().getSimpleName());
            throw UserException.oidcProviderUnavailable(exception);
        }
    }

    /**
     * provider 전용 디코더를 만든다.
     *
     * <p>{@code createDefaultWithValidators} 로 감싸 Spring 이 기본으로 거는 검증(토큰 타입 · {@code exp}/{@code nbf}
     * · 인증서 thumbprint)을 그대로 살린 채 issuer·audience 검증을 얹는다. 기본 검증을 직접 조립하면 라이브러리가
     * 나중에 추가하는 것을 놓친다.
     */
    private static JwtDecoder buildDecoder(AuthProvider.Oidc oidc, List<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwksUri())
                .restOperations(jwksClient())
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithValidators(issuerValidator(oidc.issuers()), audienceValidator(audiences)));
        return decoder;
    }

    /**
     * JWKS 를 받아올 때 쓰는 클라이언트 — <b>기본값을 그대로 두지 않는다</b>.
     *
     * <p>Spring Security 7 의 기본 연결·읽기 timeout 은 <b>30초</b>다. 이 조회는 로그인 요청 경로에서 일어나므로,
     * 제공자의 JWKS 엔드포인트가 멎으면 사용자가 30초를 기다린 뒤 실패한다. 그 사이 요청 스레드도 물려 있다.
     *
     * <p>3초로 잡는다 — 이 서비스가 외부 호출에 두는 상한과 같다. JWKS 는 정적 문서라 정상이면 수백 ms 에 온다.
     * 못 받으면 {@code USER-005}(502)로 끊고, 다음 요청이 다시 시도한다.
     */
    private static RestOperations jwksClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(JWKS_TIMEOUT);
        factory.setReadTimeout(JWKS_TIMEOUT);
        return new RestTemplate(factory);
    }

    /**
     * {@code iss} 가 이 provider 의 표기 중 하나여야 한다.
     *
     * <p>{@code JwtIssuerValidator} 를 쓰지 않는 이유는 그것이 값 하나만 받기 때문이다. Google 이 스킴 있는 표기와
     * 없는 표기를 모두 쓰는데, 하나만 허용하면 다른 표기를 받은 사용자가 전부 401 이 된다.
     *
     * <p>{@code getIssuer()}(URL) 가 아니라 클레임 문자열로 비교한다 — 스킴 없는 {@code accounts.google.com} 은
     * URL 로 해석되지 않아 비교 자체가 성립하지 않는다.
     */
    private static OAuth2TokenValidator<Jwt> issuerValidator(List<String> issuers) {
        return token -> {
            if (issuers.contains(token.getClaimAsString(JwtClaimNames.ISS))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", ISSUER_MISMATCH, null));
        };
    }

    /** aud 가 우리 클라이언트 ID 중 하나여야 한다 — 남의 앱용 토큰을 그대로 받아주면 계정 탈취가 된다. */
    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> audiences) {
        return token -> {
            List<String> tokenAudiences = token.getAudience();
            if (tokenAudiences != null && tokenAudiences.stream().anyMatch(audiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", AUDIENCE_MISMATCH, null));
        };
    }

    private static String nicknameOf(AuthProvider.Oidc oidc, Jwt jwt) {
        return oidc.nicknameClaimIfPresent().map(jwt::getClaimAsString).orElse(null);
    }

    private static String emailOf(Jwt jwt) {
        return jwt.getClaimAsString(AuthProvider.EMAIL_CLAIM);
    }
}
