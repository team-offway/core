package com.offway.core.user.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 지원하는 소셜 로그인 provider.
 *
 * <p><b>셋이 같은 방식으로 확인되지 않는다.</b> Apple·Google 이 주는 것은 provider 가 서명한 ID 토큰이라 공개키만
 * 있으면 서버 안에서 신원이 확정된다. Kakao 가 주는 것은 <b>정보가 담기지 않은 액세스 토큰</b>이라, 그 토큰으로
 * 프로필 API 를 한 번 더 불러야 누구인지 알 수 있다 — 로그인 경로에 외부 호출이 낀다는 뜻이다.
 *
 * <p>그 차이를 {@link #oidc()} 의 유무가 표현한다. 값이 있으면 서명 검증으로, 없으면 프로필 조회로 확인한다.
 * 분기를 boolean 이나 별도 enum 으로 또 두지 않는 이유는, 서명 검증에 필요한 값(issuer·JWKS 주소)과 그 방식이
 * 쓰이는 조건이 정확히 같기 때문이다.
 */
public enum AuthProvider {

    /**
     * Google 은 {@code iss} 를 <b>두 가지 표기</b>로 낸다 — {@code https://accounts.google.com} 과 스킴 없는
     * {@code accounts.google.com}. 어느 쪽이 오는지는 토큰을 만든 SDK·흐름에 달렸다. 하나만 허용하면 다른 표기를
     * 받은 사용자가 전부 401 이 되므로 둘 다 받는다(Google 자신의 검증 라이브러리도 그렇게 한다).
     */
    GOOGLE(new Oidc(
            List.of("https://accounts.google.com", "accounts.google.com"),
            "https://www.googleapis.com/oauth2/v3/certs",
            "name")),

    /** Apple 은 ID 토큰에 이름을 담지 않는다 — 최초 인증 응답에만, 그것도 사용자가 제공을 선택했을 때만 온다. */
    APPLE(new Oidc(List.of("https://appleid.apple.com"), "https://appleid.apple.com/auth/keys", null)),

    /** 액세스 토큰에 신원 정보가 없어 프로필 API 조회로 확인한다. */
    KAKAO(null);

    /** OIDC 표준 이메일 클레임. Apple·Google 이 같은 이름을 쓴다. */
    public static final String EMAIL_CLAIM = "email";

    private final Oidc oidc;

    AuthProvider(Oidc oidc) {
        this.oidc = oidc;
    }

    /** 서명 검증으로 확인하는 provider 면 그 설정. 비어 있으면 프로필 조회로 확인한다. */
    public Optional<Oidc> oidc() {
        return Optional.ofNullable(oidc);
    }

    /**
     * 경로 변수({@code /auth/callback/kakao})를 provider 로 해석한다. 대소문자를 가리지 않는다.
     *
     * <p>모르는 값에 Spring 기본 변환 실패(형식 오류)를 맡기지 않고 여기서 {@code USER-002} 로 끊는다 — 앱이
     * 문구가 아니라 code 로 분기하므로, "지원하지 않는 로그인 방식" 이라는 사유가 code 로 전달돼야 한다.
     */
    public static AuthProvider from(String value) {
        if (value == null || value.isBlank()) {
            throw UserException.unsupportedProvider();
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw UserException.unsupportedProvider();
        }
    }

    /**
     * 서명된 ID 토큰을 검증하는 데 필요한 값.
     *
     * <p>audience(클라이언트 ID)는 여기 없다 — 환경별로 다르고 비밀에 가까워 설정({@code offway.auth.oidc.*})이 소유한다.
     *
     * @param issuers 토큰의 {@code iss} 가 이 중 하나여야 한다. 표기를 여러 개 쓰는 provider(Google)가 있어 목록이다
     * @param jwksUri 서명 검증용 공개키 주소
     * @param nicknameClaim 표시 이름이 담긴 클레임. 주지 않는 provider 는 {@code null}
     */
    public record Oidc(List<String> issuers, String jwksUri, String nicknameClaim) {

        public Oidc {
            issuers = List.copyOf(issuers);
        }

        /** ID 토큰에서 닉네임을 담고 있는 클레임 이름. Apple 처럼 주지 않는 provider 는 비어 있다. */
        public Optional<String> nicknameClaimIfPresent() {
            return Optional.ofNullable(nicknameClaim);
        }
    }
}
