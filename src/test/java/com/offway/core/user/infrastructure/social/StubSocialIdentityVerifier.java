package com.offway.core.user.infrastructure.social;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import java.util.function.BiFunction;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 서명 검증 provider(Apple · Google) 외부 경계 stub — 통합 테스트에서 JWKS 호출을 격리한다.
 *
 * <p><b>{@code @Primary} 가 아니라 {@code @Order} 로 실물을 이긴다.</b> {@code DelegatingSocialIdentityResolver}
 * 는 전략을 {@code List} 로 주입받아 먼저 맞는 것을 쓰는데, 그 목록 순서는 {@code @Primary} 가 아니라
 * {@code @Order} 가 정한다. 최우선 순위를 줘야 실물 {@code NimbusOidcVerifier} 보다 앞에 선다.
 *
 * <p>카카오는 여기서 맡지 않는다 — 그쪽 외부 경계는 {@code KakaoProfileClient} 라, 그 port 를 stub 해야
 * {@code KakaoIdentityVerifier} 의 판단(미설정 차단·응답 매핑)이 실물로 검증된다.
 *
 * <p>default 동작은 throw 다. 검증 경로에 닿는 테스트가 {@code respond(...)} 로 시나리오를 지정하지 않으면 즉시
 * 깨지게 해 "이전 테스트 상태가 살아남는" 함정을 막는다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StubSocialIdentityVerifier implements SocialIdentityVerifier {

    private BiFunction<AuthProvider, String, SocialIdentity> behavior = (provider, credential) -> {
        throw new IllegalStateException(
                "StubSocialIdentityVerifier 미설정 — 테스트가 respond(...) 로 검증 동작을 지정해야 합니다.");
    };

    @Override
    public boolean supports(AuthProvider provider) {
        return provider.oidc().isPresent();
    }

    /** provider·토큰에 따라 결과를 정하거나 예외를 던지도록 지정한다. */
    public void respond(BiFunction<AuthProvider, String, SocialIdentity> behavior) {
        this.behavior = behavior;
    }

    /** 어떤 요청이든 같은 신원으로 검증 성공시킨다. */
    public void respondWith(AuthProvider provider, String providerUserId, String nickname, String email) {
        this.behavior =
                (requestedProvider, credential) -> new SocialIdentity(provider, providerUserId, nickname, email);
    }

    @Override
    public SocialIdentity verify(AuthProvider provider, String credential) {
        return behavior.apply(provider, credential);
    }
}
