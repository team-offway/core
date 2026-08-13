package com.offway.core.user.infrastructure.social;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;

/**
 * provider 하나(또는 같은 방식으로 확인되는 묶음)의 신원 확인 전략.
 *
 * <p>구현이 자기가 맡는 provider 를 스스로 밝히므로({@link #supports}) 어디에도 provider 분기(switch·if)가 없다.
 * 새 provider 는 이 인터페이스 구현을 하나 더 등록하는 것으로 끝난다.
 */
public interface SocialIdentityVerifier {

    /** 이 전략이 맡는 provider 인지. */
    boolean supports(AuthProvider provider);

    /** @see SocialIdentityResolver#resolve(AuthProvider, String) */
    SocialIdentity verify(AuthProvider provider, String credential);
}
