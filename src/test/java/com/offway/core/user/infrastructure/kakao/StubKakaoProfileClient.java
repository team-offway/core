package com.offway.core.user.infrastructure.kakao;

import java.util.function.Function;

/**
 * 카카오 API 외부 경계 stub — 통합 테스트에서 {@code kapi.kakao.com} 호출을 격리한다.
 *
 * <p>port 를 stub 하므로 {@code KakaoIdentityVerifier}·{@code DelegatingSocialIdentityResolver} 는 실물이 돈다.
 * 앱 번호 대조(우리 앱 토큰인가)도 실물 판단이라 여기서 흉내 내지 않는다 — stub 은 카카오가 <b>무엇을 답했는지</b>만
 * 정하고, 그것을 받아들일지는 검증기가 정한다.
 *
 * <p>default 동작은 throw 다 — 명시 세팅을 빠뜨린 테스트가 조용히 통과하지 않게.
 */
public class StubKakaoProfileClient implements KakaoProfileClient {

    /**
     * 테스트에서 "우리 앱" 으로 취급하는 카카오 앱 번호.
     *
     * <p>{@code src/test/resources/application-local.properties} 의 {@code offway.auth.oidc.kakao.audiences} 와
     * 같은 값이어야 한다. 어긋나면 카카오 로그인 테스트가 전부 401 이 된다.
     */
    public static final String OUR_APP_ID = "1234567";

    private Function<String, KakaoProfile> behavior = accessToken -> {
        throw new IllegalStateException("StubKakaoProfileClient 미설정 — 테스트가 respond(...) 로 응답을 지정해야 합니다.");
    };

    /** 토큰 정보 조회 응답. 기본은 우리 앱이 발급한 토큰이다 — 앱 번호를 다루지 않는 테스트가 그 사실을 몰라도 되게. */
    private Function<String, KakaoTokenInfo> tokenInfoBehavior = ourAppTokenInfo();

    private static Function<String, KakaoTokenInfo> ourAppTokenInfo() {
        return accessToken -> new KakaoTokenInfo("token-info-id", OUR_APP_ID);
    }

    /**
     * 액세스 토큰에 따라 프로필 결과를 정하거나 예외를 던지도록 지정한다.
     *
     * <p><b>토큰 정보 응답도 기본값(우리 앱)으로 되돌린다.</b> 이 stub 은 빈이라 클래스 안의 테스트들이 같은
     * 인스턴스를 공유하는데, 앞선 테스트가 남긴 "남의 앱 토큰" 이 뒤 테스트로 새면 엉뚱한 401 이 난다. 앱 번호를
     * 다루는 테스트는 이 호출 <b>뒤에</b> 지정한다.
     */
    public void respond(Function<String, KakaoProfile> behavior) {
        this.behavior = behavior;
        this.tokenInfoBehavior = ourAppTokenInfo();
    }

    /** 어떤 토큰이든 같은 프로필로 성공시킨다. 사진은 없는 것으로 둔다(동의 거부·기본 이미지). */
    public void respondWith(String id, String nickname, String email) {
        respondWith(id, nickname, email, null);
    }

    /** 프로필 사진까지 주는 응답(#308) — 사진이 저장·노출되는 경로를 보는 테스트용. */
    public void respondWith(String id, String nickname, String email, String profileImageUrl) {
        respond(accessToken -> new KakaoProfile(id, nickname, email, profileImageUrl));
    }

    /** 토큰 정보 조회 결과를 정한다 — 남의 앱 토큰·조회 실패를 흉내 낼 때 쓴다. */
    public void respondTokenInfo(Function<String, KakaoTokenInfo> tokenInfoBehavior) {
        this.tokenInfoBehavior = tokenInfoBehavior;
    }

    /** 이 토큰이 주어진 앱에서 발급된 것으로 답하게 한다. */
    public void respondTokenInfoFromApp(String appId) {
        this.tokenInfoBehavior = accessToken -> new KakaoTokenInfo("token-info-id", appId);
    }

    @Override
    public KakaoProfile fetchProfile(String accessToken) {
        return behavior.apply(accessToken);
    }

    @Override
    public KakaoTokenInfo fetchTokenInfo(String accessToken) {
        return tokenInfoBehavior.apply(accessToken);
    }
}
