package com.offway.core.user.infrastructure.social;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;

/**
 * 앱이 넘긴 provider 토큰으로 신원을 확인하는 port — 서비스가 의존하는 유일한 외부 경계.
 *
 * <p>provider 별로 확인 방식이 다르지만({@link SocialIdentityVerifier}) 서비스는 그것을 몰라야 한다. 통합 테스트는
 * 이 port 하나를 stub 으로 갈아끼운다.
 */
public interface SocialIdentityResolver {

    /**
     * provider 토큰을 확인하고 신원을 돌려준다.
     *
     * @param provider 어느 provider 로 로그인하는지
     * @param credential 앱이 provider SDK 에서 받아 넘긴 토큰. Apple·Google 은 ID 토큰(JWT), Kakao 는 액세스 토큰
     * @throws com.offway.core.user.domain.UserException 토큰이 무효({@code USER-001})거나, provider 가 설정되지
     *     않았거나({@code USER-002}), provider 를 부르지 못했을 때({@code USER-005})
     */
    SocialIdentity resolve(AuthProvider provider, String credential);
}
