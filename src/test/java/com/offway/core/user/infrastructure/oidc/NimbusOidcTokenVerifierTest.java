package com.offway.core.user.infrastructure.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserErrorCode;
import com.offway.core.user.domain.UserException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NimbusOidcTokenVerifierTest {

    /** audience 가 비어 있으면 provider 를 쓸 수 없다 — 네트워크에 나가기 전에 걸러야 한다. */
    @ParameterizedTest
    @EnumSource(AuthProvider.class)
    void audience가_설정되지_않은_provider는_USER_002로_거부한다(AuthProvider provider) {
        NimbusOidcTokenVerifier verifier = new NimbusOidcTokenVerifier(new AuthProperties(null, Map.of()));

        UserException exception = assertThrows(UserException.class, () -> verifier.verify(provider, "any-token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    @Test
    void 다른_provider가_설정돼_있어도_요청한_provider가_비면_거부한다() {
        AuthProperties properties = new AuthProperties(
                null, Map.of(AuthProvider.GOOGLE, new AuthProperties.Oidc(List.of("google-client-id"))));
        NimbusOidcTokenVerifier verifier = new NimbusOidcTokenVerifier(properties);

        UserException exception =
                assertThrows(UserException.class, () -> verifier.verify(AuthProvider.APPLE, "any-token"));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }
}
