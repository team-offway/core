package com.offway.core.user.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * ID 토큰 검증 결과 — provider 가 확인해 준 신원.
 *
 * <p>어댑터 DTO 가 아니라 도메인 타입이라, 검증 구현(Nimbus·stub)이 바뀌어도 서비스가 흔들리지 않는다.
 *
 * @param provider 어느 provider 가 확인해 줬는지
 * @param subject ID 토큰의 {@code sub}. provider 안에서 유일하고 변하지 않는 유일한 값이라 계정 매칭 키로 쓴다
 * @param nickname provider 가 준 표시 이름. Apple 은 주지 않으므로 비어 있을 수 있다
 */
public record OidcUser(AuthProvider provider, String subject, String nickname) {

    public OidcUser {
        Objects.requireNonNull(provider, "provider 는 필수입니다");
        Objects.requireNonNull(subject, "subject 는 필수입니다");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject 는 비어 있을 수 없습니다");
        }
    }

    public Optional<String> nicknameIfPresent() {
        return Optional.ofNullable(nickname).filter(value -> !value.isBlank());
    }
}
