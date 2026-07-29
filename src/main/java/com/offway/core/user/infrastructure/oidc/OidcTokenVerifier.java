package com.offway.core.user.infrastructure.oidc;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.OidcUser;

/**
 * provider ID 토큰 검증 port — 도메인이 의존하는 외부 경계.
 *
 * <p>구현은 provider 공개키(JWKS)로 서명과 {@code iss}·{@code aud}·{@code exp} 를 확인한다. 테스트는 이 port 를
 * stub 으로 갈아끼운다.
 */
public interface OidcTokenVerifier {

    /**
     * ID 토큰을 검증하고 확인된 신원을 돌려준다.
     *
     * @throws com.offway.core.user.domain.UserException 토큰이 무효({@code USER-001})거나, provider 가 설정되지
     *     않았거나({@code USER-002}), provider 공개키를 가져오지 못했을 때({@code USER-005})
     */
    OidcUser verify(AuthProvider provider, String idToken);
}
