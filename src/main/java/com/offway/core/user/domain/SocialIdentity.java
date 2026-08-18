package com.offway.core.user.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * provider 가 확인해 준 신원 — <b>서버가 스스로 확인한 값만</b> 담는다.
 *
 * <p>어댑터 DTO 가 아니라 도메인 타입이라, 확인 방식(서명 검증·프로필 조회·stub)이 바뀌어도 서비스가 흔들리지 않는다.
 *
 * <p><b>클라이언트가 보낸 값은 여기에 들어오지 않는다.</b> 앱도 요청 본문에 {@code providerUserId} 를 실어 보내지만,
 * 그 값을 그대로 쓰면 아무나 남의 식별자를 적어 그 계정으로 로그인할 수 있다 — 계정 탈취가 요청 한 번이 된다.
 * {@link #providerUserId} 는 Apple·Google 은 검증된 ID 토큰의 {@code sub}, Kakao 는 프로필 API 가 돌려준
 * {@code id} 에서만 온다.
 *
 * @param provider 어느 provider 가 확인해 줬는지
 * @param providerUserId provider 안에서 유일하고 변하지 않는 식별자. 계정 매칭 키다
 * @param nickname provider 가 준 표시 이름. Apple 은 주지 않으므로 비어 있을 수 있다
 * @param email provider 가 준 이메일. Kakao 는 동의를 거부할 수 있고 Apple 은 Private Relay 익명 주소를 줄 수 있어
 *     비어 있거나 실제 주소가 아닐 수 있다. 계정 매칭에는 쓰지 않는다
 * @param audience 이 토큰이 <b>어느 클라이언트로 발급됐는지</b>(검증된 {@code aud}). ID 토큰을 검증한 provider 만
 *     채운다 — 프로필 API 로 확인하는 Kakao 는 없다
 */
public record SocialIdentity(
        AuthProvider provider, String providerUserId, String nickname, String email, String audience) {

    /**
     * {@code aud} 를 모르는 provider 용 — Kakao 처럼 ID 토큰을 안 쓰는 쪽이다.
     *
     * <p>편의 생성자를 두는 이유는 {@code aud} 가 <b>있는 것이 예외</b>가 아니라 <b>없는 것이 정상</b>인
     * provider 가 있기 때문이다. 모든 호출부에 {@code null} 을 적게 하면 그 의미가 흐려진다.
     */
    public SocialIdentity(AuthProvider provider, String providerUserId, String nickname, String email) {
        this(provider, providerUserId, nickname, email, null);
    }

    public SocialIdentity {
        Objects.requireNonNull(provider, "provider 는 필수입니다");
        Objects.requireNonNull(providerUserId, "provider 사용자 식별자는 필수입니다");
        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException("provider 사용자 식별자는 비어 있을 수 없습니다");
        }
    }

    /** 이 토큰을 발급한 클라이언트 — 탈퇴 때 <b>같은 클라이언트로 서명</b>해야 provider 가 해제를 받아준다. */
    public Optional<String> audienceIfPresent() {
        return blankToEmpty(audience);
    }

    public Optional<String> nicknameIfPresent() {
        return blankToEmpty(nickname);
    }

    public Optional<String> emailIfPresent() {
        return blankToEmpty(email);
    }

    private static Optional<String> blankToEmpty(String value) {
        return Optional.ofNullable(value).filter(candidate -> !candidate.isBlank());
    }
}
