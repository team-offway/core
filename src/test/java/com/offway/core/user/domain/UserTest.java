package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    private static final String DEFAULT_NICKNAME = "여행자";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void 닉네임이_비면_기본_표시_이름을_쓴다(String nickname) {
        // Apple 은 ID 토큰에 이름을 주지 않아 실제로 발생한다. 닉네임 때문에 가입이 실패하면 안 된다.
        assertEquals(DEFAULT_NICKNAME, User.withNickname(nickname).getNickname());
    }

    @Test
    void 닉네임_앞뒤_공백은_제거한다() {
        assertEquals("세빈", User.withNickname("  세빈  ").getNickname());
    }

    @Test
    void 닉네임이_컬럼_폭을_넘으면_잘라낸다() {
        // provider 가 주는 값이라 우리가 길이를 통제하지 못한다 — 저장 단계 서버 오류로 새지 않게 경계에서 자른다.
        String tooLong = "가".repeat(User.MAX_NICKNAME_LENGTH + 10);

        String nickname = User.withNickname(tooLong).getNickname();

        assertEquals(User.MAX_NICKNAME_LENGTH, nickname.length());
    }

    @Test
    void 이메일은_없을_수_있다() {
        // Kakao 는 이메일 동의를 거부할 수 있고, Apple 은 최초 로그인에만 준다. 없는 것이 정상 경로다.
        assertNull(User.of("세빈", null).getEmail());
        assertNull(User.of("세빈", "   ").getEmail());
    }

    @Test
    void 이메일_앞뒤_공백은_제거한다() {
        assertEquals("user@example.com", User.of("세빈", "  user@example.com  ").getEmail());
    }

    @Test
    void 이메일이_컬럼_폭을_넘으면_잘라낸다() {
        // 형식을 강제하지 않는다 — provider 가 주는 값이라 우리가 통제하지 못하고, 이메일 하나 때문에
        // 가입이 실패하면 안 된다. 길이만 컬럼에 맞춘다.
        String tooLong = "a".repeat(User.MAX_EMAIL_LENGTH + 10);

        assertEquals(User.MAX_EMAIL_LENGTH, User.of("세빈", tooLong).getEmail().length());
    }

    @Test
    void 이름을_바꾸면_같은_정규화_규칙이_적용된다() {
        User user = User.withNickname("세빈");

        user.rename("   ");

        assertEquals(DEFAULT_NICKNAME, user.getNickname());
    }
}
