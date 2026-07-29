package com.offway.core.user.domain;

import java.util.Optional;

/**
 * 지원하는 OAuth provider. 셋 다 OIDC ID 토큰을 주므로 서버는 하나의 검증 경로로 처리한다.
 *
 * <p>issuer·jwksUri 를 상수별로 보유해 provider 분기를 없앤다. 반면 audience(클라이언트 ID)는 환경별로 다르고 Google 은
 * iOS/Android 값이 달라 복수라, enum 이 아니라 설정({@code offway.auth.oidc.*.audiences})이 소유한다.
 */
public enum AuthProvider {

    GOOGLE("https://accounts.google.com", "https://www.googleapis.com/oauth2/v3/certs", "name"),

    KAKAO("https://kauth.kakao.com", "https://kauth.kakao.com/.well-known/jwks.json", "nickname"),

    /** Apple 은 ID 토큰에 이름을 담지 않는다 — 최초 인증 응답에만, 그것도 사용자가 제공을 선택했을 때만 온다. */
    APPLE("https://appleid.apple.com", "https://appleid.apple.com/auth/keys", null);

    private final String issuer;
    private final String jwksUri;
    private final String nicknameClaim;

    AuthProvider(String issuer, String jwksUri, String nicknameClaim) {
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.nicknameClaim = nicknameClaim;
    }

    public String issuer() {
        return issuer;
    }

    public String jwksUri() {
        return jwksUri;
    }

    /** ID 토큰에서 닉네임을 담고 있는 클레임 이름. Apple 처럼 주지 않는 provider 는 비어 있다. */
    public Optional<String> nicknameClaim() {
        return Optional.ofNullable(nicknameClaim);
    }
}
