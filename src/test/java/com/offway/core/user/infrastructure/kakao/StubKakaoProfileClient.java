package com.offway.core.user.infrastructure.kakao;

import java.util.function.Function;

/**
 * 카카오 프로필 API 외부 경계 stub — 통합 테스트에서 {@code kapi.kakao.com} 호출을 격리한다.
 *
 * <p>port 를 stub 하므로 {@code KakaoIdentityVerifier}·{@code DelegatingSocialIdentityResolver} 는 실물이 돈다.
 *
 * <p>default 동작은 throw 다 — 명시 세팅을 빠뜨린 테스트가 조용히 통과하지 않게.
 */
public class StubKakaoProfileClient implements KakaoProfileClient {

    private Function<String, KakaoProfile> behavior = accessToken -> {
        throw new IllegalStateException("StubKakaoProfileClient 미설정 — 테스트가 respond(...) 로 응답을 지정해야 합니다.");
    };

    /** 액세스 토큰에 따라 결과를 정하거나 예외를 던지도록 지정한다. */
    public void respond(Function<String, KakaoProfile> behavior) {
        this.behavior = behavior;
    }

    /** 어떤 토큰이든 같은 프로필로 성공시킨다. */
    public void respondWith(String id, String nickname, String email) {
        this.behavior = accessToken -> new KakaoProfile(id, nickname, email);
    }

    @Override
    public KakaoProfile fetchProfile(String accessToken) {
        return behavior.apply(accessToken);
    }
}
