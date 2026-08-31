package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.domain.OAuthState;
import java.util.function.Function;

/**
 * 카카오 인증 서버({@code kauth.kakao.com}) 경계 stub — 웹 로그인 통합 테스트가 쓴다(#343).
 *
 * <p>port 를 stub 하므로 그 뒤의 신원 확인은 실물이 돈다. 교환한 토큰이 앱이 넘긴 토큰과 같은 길을 탄다는
 * 것이 이 설계의 핵심이라, 그 길까지 흉내 내면 확인하려던 것이 사라진다.
 *
 * <p>{@link #authorizationUri} 는 던지지 않는다 — 주소 조립은 외부 호출이 아니라 순수 계산이고, 리다이렉트
 * 목적지를 보는 테스트가 매번 지정하게 하면 잡음만 는다. 대신 실물과 같은 호스트를 써서 "카카오로 보냈다"
 * 는 단언이 뜻을 갖게 한다.
 *
 * <p>{@link #exchange} 의 default 동작은 throw 다 — 명시 세팅을 빠뜨린 테스트가 조용히 통과하지 않게.
 */
public class StubKakaoOAuthClient implements KakaoOAuthClient {

    /** 실물이 쓰는 인증 서버. 리다이렉트 단언이 이 값을 본다. */
    public static final String AUTHORIZE_HOST = "https://kauth.kakao.com/oauth/authorize";

    private Function<String, String> behavior = code -> {
        throw new IllegalStateException("StubKakaoOAuthClient 미설정 — 테스트가 respond(...) 로 응답을 지정해야 합니다.");
    };

    /** 인가 코드에 따라 액세스 토큰을 주거나 예외를 던지도록 지정한다. */
    public void respond(Function<String, String> behavior) {
        this.behavior = behavior;
    }

    /** 어떤 코드든 같은 액세스 토큰으로 교환된 것으로 둔다. */
    public void respondWith(String accessToken) {
        respond(code -> accessToken);
    }

    @Override
    public String authorizationUri(OAuthState state) {
        return AUTHORIZE_HOST + "?state=" + state.value();
    }

    @Override
    public String exchange(String authorizationCode) {
        return behavior.apply(authorizationCode);
    }
}
