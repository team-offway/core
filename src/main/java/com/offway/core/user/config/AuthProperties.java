package com.offway.core.user.config;

import com.offway.core.user.domain.AuthProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정 — 자체 JWT 서명키·수명과 provider 별 audience.
 *
 * <p>audience(클라이언트 ID)가 비어 있어도 부팅은 된다(로컬 실행성 규칙). 해당 provider 로그인만 {@code USER-002} 로 실패한다.
 */
@ConfigurationProperties(prefix = "offway.auth")
public record AuthProperties(Jwt jwt, Map<AuthProvider, Oidc> oidc) {

    public AuthProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null);
        }
        oidc = oidc == null ? Map.of() : Map.copyOf(oidc);
    }

    /** provider 에 설정된 audience 목록. 비어 있으면 그 provider 는 사용 불가다. */
    public List<String> audiencesOf(AuthProvider provider) {
        Oidc config = oidc.get(provider);
        return config == null ? List.of() : config.audiences();
    }

    /**
     * @param secret HS256 서명키. 최소 32바이트. local 은 개발용 고정값, prod 는 환경변수 필수
     * @param accessTtl access 토큰 수명
     * @param refreshTtl refresh 토큰 수명
     */
    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {

        private static final Duration DEFAULT_ACCESS_TTL = Duration.ofHours(1);
        private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(60);

        public Jwt {
            accessTtl = accessTtl == null ? DEFAULT_ACCESS_TTL : accessTtl;
            refreshTtl = refreshTtl == null ? DEFAULT_REFRESH_TTL : refreshTtl;
        }
    }

    /** @param audiences 허용할 클라이언트 ID. Google 은 iOS/Android 가 달라 복수다. */
    public record Oidc(List<String> audiences) {

        public Oidc {
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
        }
    }
}
