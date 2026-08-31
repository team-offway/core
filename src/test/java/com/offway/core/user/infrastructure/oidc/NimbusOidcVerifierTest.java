package com.offway.core.user.infrastructure.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserErrorCode;
import com.offway.core.user.domain.UserException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NimbusOidcVerifierTest {

    @ParameterizedTest
    @EnumSource(
            value = AuthProvider.class,
            names = {"GOOGLE", "APPLE"})
    void 서명된_ID토큰을_주는_provider만_맡는다(AuthProvider provider) {
        NimbusOidcVerifier verifier = new NimbusOidcVerifier(new AuthProperties(null, Map.of(), null, null));

        assertTrue(verifier.supports(provider));
    }

    @Test
    void 카카오는_맡지_않는다() {
        // 액세스 토큰에는 서명된 신원이 없어 공개키로 확인할 것이 없다.
        NimbusOidcVerifier verifier = new NimbusOidcVerifier(new AuthProperties(null, Map.of(), null, null));

        assertFalse(verifier.supports(AuthProvider.KAKAO));
    }

    /** audience 가 비어 있으면 aud 검증이 무력화된다 — 네트워크에 나가기 전에 걸러야 한다. */
    @ParameterizedTest
    @EnumSource(
            value = AuthProvider.class,
            names = {"GOOGLE", "APPLE"})
    void audience가_설정되지_않은_provider는_USER_002로_거부한다(AuthProvider provider) {
        NimbusOidcVerifier verifier = new NimbusOidcVerifier(new AuthProperties(null, Map.of(), null, null));

        UserException exception = assertThrows(UserException.class, () -> verifier.verify(provider, "any-token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    @Test
    void 다른_provider가_설정돼_있어도_요청한_provider가_비면_거부한다() {
        AuthProperties properties = new AuthProperties(
                null,
                Map.of(AuthProvider.GOOGLE, new AuthProperties.Oidc(List.of("google-web-client-id"), null, null, null)),
                null,
                null);
        NimbusOidcVerifier verifier = new NimbusOidcVerifier(properties);

        UserException exception =
                assertThrows(UserException.class, () -> verifier.verify(AuthProvider.APPLE, "any-token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }
}
