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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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

    private static final String AUDIENCE_MISMATCH = "audience 가 일치하지 않습니다.";

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

    private static JwtDecoder buildDecoder(AuthProvider.Oidc oidc, List<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwksUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(oidc.issuer()), audienceValidator(audiences)));
        return decoder;
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
