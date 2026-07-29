package com.offway.core.user.infrastructure.oidc;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.OidcUser;
import java.util.function.BiFunction;

/**
 * {@link OidcTokenVerifier} 외부 경계 stub — 통합 테스트에서 provider JWKS 호출을 격리한다.
 *
 * <p>default 동작은 throw 다. 검증 경로에 닿는 테스트가 {@code respond(...)} 로 시나리오를 지정하지 않으면 즉시 깨지게 해
 * "이전 테스트 상태가 살아남는" 함정을 막는다.
 */
public class StubOidcTokenVerifier implements OidcTokenVerifier {

    private BiFunction<AuthProvider, String, OidcUser> behavior = (provider, idToken) -> {
        throw new IllegalStateException("StubOidcTokenVerifier 미설정 — 테스트가 respond(...) 로 검증 동작을 지정해야 합니다.");
    };

    /** provider·토큰에 따라 결과를 정하거나 예외를 던지도록 지정한다. */
    public void respond(BiFunction<AuthProvider, String, OidcUser> behavior) {
        this.behavior = behavior;
    }

    /** 어떤 요청이든 같은 신원으로 검증 성공시킨다. */
    public void respondWith(AuthProvider provider, String subject, String nickname) {
        this.behavior = (requestedProvider, idToken) -> new OidcUser(provider, subject, nickname);
    }

    @Override
    public OidcUser verify(AuthProvider provider, String idToken) {
        return behavior.apply(provider, idToken);
    }
}
