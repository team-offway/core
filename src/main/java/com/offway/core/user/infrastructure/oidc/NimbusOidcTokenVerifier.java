package com.offway.core.user.infrastructure.oidc;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.OidcUser;
import com.offway.core.user.domain.UserException;
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
 * JWKS 기반 ID 토큰 검증 어댑터.
 *
 * <p>provider 별 {@link JwtDecoder} 를 만들어 캐시한다 — 디코더가 내부적으로 JWKS 를 캐시하므로 매 로그인마다 provider 를
 * 호출하지 않는다. 새 의존성 없이 {@code spring-security-oauth2-jose}(Nimbus)만 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NimbusOidcTokenVerifier implements OidcTokenVerifier {

    private static final String AUDIENCE_MISMATCH = "audience 가 일치하지 않습니다.";

    private final AuthProperties authProperties;
    private final Map<AuthProvider, JwtDecoder> decoders = new ConcurrentHashMap<>();

    @Override
    public OidcUser verify(AuthProvider provider, String idToken) {
        List<String> audiences = authProperties.audiencesOf(provider);
        if (audiences.isEmpty()) {
            log.info("설정되지 않은 provider 로그인 시도 provider={}", provider);
            throw UserException.unsupportedProvider();
        }
        Jwt jwt = decode(provider, idToken, audiences);
        return new OidcUser(provider, jwt.getSubject(), nicknameOf(provider, jwt));
    }

    private Jwt decode(AuthProvider provider, String idToken, List<String> audiences) {
        try {
            return decoders.computeIfAbsent(provider, key -> buildDecoder(key, audiences)).decode(idToken);
        } catch (BadJwtException exception) {
            // 서명 불일치·형식 오류·클레임 검증 실패 — 클라이언트가 가진 토큰의 문제라 401.
            log.info("ID 토큰 검증 실패 provider={}", provider);
            throw UserException.invalidIdToken(exception);
        } catch (JwtException exception) {
            // JWKS 조회 실패 등 provider 측 문제 — 재시도로 풀릴 수 있으므로 502 로 구분한다.
            log.warn("provider 공개키 조회 실패 provider={}", provider, exception);
            throw UserException.oidcProviderUnavailable(exception);
        }
    }

    private static JwtDecoder buildDecoder(AuthProvider provider, List<String> audiences) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.jwksUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(provider.issuer()), audienceValidator(audiences)));
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

    private static String nicknameOf(AuthProvider provider, Jwt jwt) {
        return provider.nicknameClaim().map(jwt::getClaimAsString).orElse(null);
    }
}
