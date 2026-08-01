package com.offway.core.user.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 인증 계정이 비면 <b>부팅을 막는다</b>는 불변식(#122).
 *
 * <p>외부 API 키는 없어도 그 호출만 죽어서 부팅을 열어두지만(로컬 실행성 규칙), 인증 계정이 비면 서버가
 * <b>통째로 열린 채</b> 뜬다. 조용히 뜨는 쪽이 훨씬 위험해서 여기만 반대로 잡는다. 이 예외가 빈 생성을
 * 실패시켜 컨텍스트 초기화가 중단된다.
 */
class BasicAuthPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 사용자명이_비면_부팅을_막는다(String blank) {
        assertThrows(IllegalStateException.class, () -> new BasicAuthProperties(blank, "{noop}pw"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 비밀번호가_비면_부팅을_막는다(String blank) {
        assertThrows(IllegalStateException.class, () -> new BasicAuthProperties("user", blank));
    }

    @Test
    void 둘_다_채워져_있으면_생성된다() {
        assertDoesNotThrow(() -> new BasicAuthProperties("teamoffway", "{bcrypt}$2y$10$hash"));
    }
}
