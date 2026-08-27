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
public record AuthProperties(Jwt jwt, Map<AuthProvider, Oidc> oidc, Apple apple, ProviderToken providerToken) {

    public AuthProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null);
        }
        oidc = oidc == null ? Map.of() : Map.copyOf(oidc);
        if (apple == null) {
            apple = new Apple(null, null, null);
        }
        if (providerToken == null) {
            providerToken = new ProviderToken(null);
        }
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
     * Apple 서버와 <b>우리가 직접</b> 이야기하기 위한 자격(#287).
     *
     * <p>ID 토큰 검증({@code oidc})과 다르다. 그쪽은 Apple 의 공개키로 서명만 확인하면 되지만, 토큰 교환·해제는
     * 우리가 <b>클라이언트임을 증명</b>해야 한다 — Apple 은 client secret 을 문자열이 아니라 {@code .p8} 로
     * 서명한 ES256 JWT 로 받는다.
     *
     * <p><b>없으면 비활성이다.</b> 셋 중 하나만 비어도 연결 해제를 하지 않는다. 부팅을 막지 않는 이유는 로컬
     * 실행성 불변식이다 — 키 없이도 뜨고 로그인도 돼야 한다(CLAUDE.md).
     *
     * @param teamId Apple Developer 팀 식별자. client secret 의 {@code iss}
     * @param keyId {@code .p8} 키의 식별자. JWT 헤더의 {@code kid}
     * @param privateKeyBase64 {@code .p8} 파일 전체를 base64 로. 개행이 환경변수에 섞이지 않게
     */
    /**
     * provider 갱신 토큰을 저장 전에 암호화할 키(#301).
     *
     * <p><b>없어도 부팅한다.</b> local 프로파일은 시크릿 없이 떠야 한다(로컬 실행성 불변식). 키가 없으면
     * 암호화를 못 하므로 그 토큰을 <b>저장하지 않는다</b> — 평문으로 흘려 넣지 않는다. 결과는 "Apple 연결
     * 해제만 건너뛰는 사용자" 이고 이미 지원되는 경로다.
     *
     * @param keyBase64 AES-256 키(32바이트)를 base64 로. {@code openssl rand -base64 32} 로 만든다
     */
    public record ProviderToken(String keyBase64) {}

    public record Apple(String teamId, String keyId, String privateKeyBase64) {

        /** 셋이 다 있어야 Apple 과 이야기할 수 있다. 하나라도 없으면 연결 해제를 건너뛴다. */
        public boolean configured() {
            return hasText(teamId) && hasText(keyId) && hasText(privateKeyBase64);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
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
