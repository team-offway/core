package com.offway.core.user.infrastructure.social;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 등록된 전략 중 해당 provider 를 맡는 것에 위임한다.
 *
 * <p>맡는 전략이 없으면 {@code USER-002} — 지원하지 않는 로그인 방식이다. 여기서 조용히 넘어가면 "왜 로그인이 안 되지"
 * 가 로그 없이 끝나므로 사유를 남긴다.
 */
@Slf4j
@Component
public class DelegatingSocialIdentityResolver implements SocialIdentityResolver {

    private final List<SocialIdentityVerifier> verifiers;

    public DelegatingSocialIdentityResolver(List<SocialIdentityVerifier> verifiers) {
        this.verifiers = List.copyOf(verifiers);
    }

    @Override
    public SocialIdentity resolve(AuthProvider provider, String credential) {
        return verifiers.stream()
                .filter(verifier -> verifier.supports(provider))
                .findFirst()
                .orElseThrow(() -> {
                    log.info("맡는 검증 전략이 없는 provider 로그인 시도 provider={}", provider);
                    return UserException.unsupportedProvider();
                })
                .verify(provider, credential);
    }
}
