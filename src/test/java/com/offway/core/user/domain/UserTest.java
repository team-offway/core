package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * 사진 없이 만들면 null 이다 — <b>빈 문자열로 채우지 않는다</b>(#308).
     *
     * <p>앱이 "없다" 와 "빈 값" 을 구분 못 해 깨진 이미지 자리를 그린다. Apple 로그인이 늘 이 경로다.
     */
    @Test
    void 사진이_없으면_null_이다() {
        assertNull(User.of("세빈", null, null).getProfileImageUrl());
        assertNull(User.of("세빈", null, "   ").getProfileImageUrl());
    }

    @Test
    void 사진_주소가_길면_컬럼_폭에_맞게_자른다() {
        // 카카오 CDN 주소는 쿼리스트링을 달고 온다. 길다고 가입이 실패하면 안 된다.
        String tooLong = "https://cdn.example.com/" + "a".repeat(User.MAX_PROFILE_IMAGE_URL_LENGTH);

        assertEquals(
                User.MAX_PROFILE_IMAGE_URL_LENGTH, User.of("세빈", null, tooLong).getProfileImageUrl().length());
    }

    /**
     * <b>빈 값으로는 지우지 않는다.</b> 로그인마다 갱신하는데 동의를 잠시 껐거나 응답이 비어 온 회차에
     * 기존 주소를 지우면 멀쩡히 보이던 사진이 사라진다.
     */
    @Test
    void 빈_값으로는_기존_사진을_지우지_않는다() {
        User user = User.of("세빈", null, "https://cdn.example.com/a.jpg");

        assertFalse(user.changeProfileImage(null));
        assertFalse(user.changeProfileImage("  "));

        assertEquals("https://cdn.example.com/a.jpg", user.getProfileImageUrl());
    }

    /** 안 바뀐 회차에 UPDATE 를 만들지 않으려고 변경 여부를 돌려준다. */
    @Test
    void 같은_주소면_바뀌지_않았다고_답한다() {
        User user = User.of("세빈", null, "https://cdn.example.com/a.jpg");

        assertFalse(user.changeProfileImage("https://cdn.example.com/a.jpg"));
        assertTrue(user.changeProfileImage("https://cdn.example.com/b.jpg"));

        assertEquals("https://cdn.example.com/b.jpg", user.getProfileImageUrl());
    }

    @Test
    void 사진이_없던_사용자도_로그인하면_채워진다() {
        // 이 필드가 지금 추가되는 것이라 기존 사용자는 전부 비어 있다. 갱신 경로가 없으면 영영 안 보인다.
        User user = User.of("세빈", null, null);

        assertTrue(user.changeProfileImage("https://cdn.example.com/a.jpg"));

        assertEquals("https://cdn.example.com/a.jpg", user.getProfileImageUrl());
    }
}
