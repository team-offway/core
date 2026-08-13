package com.offway.core.user.infrastructure.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserErrorCode;
import com.offway.core.user.domain.UserException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelegatingSocialIdentityResolverTest {

    @Test
    void 맡는_전략에_위임한다() {
        SocialIdentityResolver resolver = new DelegatingSocialIdentityResolver(
                List.of(verifierFor(AuthProvider.GOOGLE, "google-user"), verifierFor(AuthProvider.KAKAO, "kakao-user")));

        assertEquals("kakao-user", resolver.resolve(AuthProvider.KAKAO, "token").providerUserId());
    }

    @Test
    void 맡는_전략이_없으면_USER_002다() {
        // provider 는 아는데 검증 수단이 등록되지 않은 상태 — 조용히 통과시키면 신원 없이 로그인이 된다.
        SocialIdentityResolver resolver =
                new DelegatingSocialIdentityResolver(List.of(verifierFor(AuthProvider.GOOGLE, "google-user")));

        UserException exception =
                assertThrows(UserException.class, () -> resolver.resolve(AuthProvider.APPLE, "token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    @Test
    void 전략이_하나도_없어도_터지지_않고_USER_002로_끊는다() {
        SocialIdentityResolver resolver = new DelegatingSocialIdentityResolver(List.of());

        UserException exception =
                assertThrows(UserException.class, () -> resolver.resolve(AuthProvider.GOOGLE, "token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    private static SocialIdentityVerifier verifierFor(AuthProvider supported, String providerUserId) {
        return new SocialIdentityVerifier() {

            @Override
            public boolean supports(AuthProvider provider) {
                return provider == supported;
            }

            @Override
            public SocialIdentity verify(AuthProvider provider, String credential) {
                return new SocialIdentity(provider, providerUserId, null, null);
            }
        };
    }
}
