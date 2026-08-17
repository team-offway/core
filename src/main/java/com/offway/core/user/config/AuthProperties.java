package com.offway.core.user.config;

import com.offway.core.user.domain.AuthProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정 — 자체 JWT 서명키·수명과 provider 별 설정.
 *
 * <p>provider 설정이 비어 있어도 부팅은 된다(로컬 실행성 규칙). 해당 provider 로그인만 {@code USER-002} 로 실패한다.
 *
 * <p>provider 마다 필요한 값이 다르다. Apple·Google 은 ID 토큰의 {@code aud} 를 대조할 <b>audience</b> 가, Kakao 는
 * 앱이 등록됐는지 판별할 <b>REST API 키</b>가 필요하다 — 확인 방식이 다르니 설정도 같을 수 없다.
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

    /** 카카오 앱이 등록됐는지. REST API 키의 존재로 판별한다. */
    public boolean kakaoConfigured() {
        Oidc config = oidc.get(AuthProvider.KAKAO);
        return config != null && config.restApiKey() != null && !config.restApiKey().isBlank();
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

    /**
     * provider 하나의 설정. 쓰이는 항목이 provider 마다 다르고, 안 쓰는 쪽은 비어 있다.
     *
     * @param audiences <b>이 토큰이 우리 앱 것인지 판별하는 값.</b> provider 마다 이름이 다를 뿐 역할은 하나다 —
     *     Apple·Google 은 ID 토큰의 {@code aud} 와 대조할 클라이언트 ID, Kakao 는 토큰 정보 조회가 돌려주는
     *     {@code app_id} 와 대조할 앱 번호다. Google 은 '웹' 클라이언트 ID 다 — iOS 클라이언트 ID 를 넣으면 앱이
     *     보낸 토큰의 {@code aud} 와 어긋나 전부 401 이 된다. 여러 개면 콤마로 나열한다
     * @param restApiKey 카카오 REST API 키. 프로필 조회 호출에 실리지는 않고, 앱 등록 여부 판별에만 쓴다
     */
    public record Oidc(List<String> audiences, String restApiKey) {

        public Oidc {
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
        }
    }
}
