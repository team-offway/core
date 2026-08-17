package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialIdentityTest {

    @Test
    void provider_식별자가_없으면_만들_수_없다() {
        // 식별자 없는 신원은 계정을 못 찾는다 — 여기서 막지 않으면 엉뚱한 계정에 붙거나 매 로그인마다 가입된다.
        assertThrows(NullPointerException.class, () -> new SocialIdentity(AuthProvider.KAKAO, null, "세빈", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void provider_식별자가_비면_만들_수_없다(String providerUserId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SocialIdentity(AuthProvider.KAKAO, providerUserId, "세빈", null));
    }

    @Test
    void provider가_없으면_만들_수_없다() {
        assertThrows(NullPointerException.class, () -> new SocialIdentity(null, "sub-1", "세빈", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 닉네임_이메일은_없을_수_있다(String absent) {
        // Apple 은 이름을 주지 않고, Kakao 는 동의를 거부할 수 있다. 그때도 로그인은 성립해야 한다.
        SocialIdentity identity = new SocialIdentity(AuthProvider.APPLE, "sub-1", absent, absent);

        assertTrue(identity.nicknameIfPresent().isEmpty());
        assertTrue(identity.emailIfPresent().isEmpty());
    }

    @Test
    void 값이_있으면_그대로_돌려준다() {
        SocialIdentity identity = new SocialIdentity(AuthProvider.GOOGLE, "sub-1", "세빈", "user@example.com");

        assertEquals("세빈", identity.nicknameIfPresent().orElseThrow());
        assertEquals("user@example.com", identity.emailIfPresent().orElseThrow());
    }
}
