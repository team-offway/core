package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.domain.OAuthState;

/**
 * 브라우저용 카카오 로그인(#343) — <b>앱에는 없는 두 단계</b>를 소유한다.
 *
 * <p>앱은 카카오 SDK 가 인가와 토큰 교환을 다 끝낸 뒤 액세스 토큰만 우리에게 넘긴다. 브라우저에는 그
 * SDK 가 없어 우리가 직접 해야 하고, 그 두 단계가 여기 모여 있다.
 *
 * <ol>
 *   <li>{@link #authorizationUri} — 사용자를 카카오 동의 화면으로 보낼 주소
 *   <li>{@link #exchange} — 돌아온 1회용 코드를 액세스 토큰으로
 * </ol>
 *
 * <p><b>여기까지가 웹에만 다른 전부다.</b> 교환한 액세스 토큰은 앱이 넘긴 것과 구분되지 않으므로, 그
 * 뒤의 신원 확인(발급 앱 대조 · 회원번호 조회)과 로그인은 앱과 같은 길을 탄다. 그래서 같은 사람이 앱으로
 * 들어오든 백오피스로 들어오든 <b>같은 회원번호</b>가 나오고, 어드민 화이트리스트가 그대로 맞는다.
 *
 * <p>두 단계를 한 port 에 두는 이유는 <b>{@code redirect_uri} 를 양쪽이 같이 써야</b> 하기 때문이다.
 * 카카오가 인가 때 받은 값과 교환 때 받은 값을 대조하므로, 둘이 갈라지면 조용히 어긋난다.
 */
public interface KakaoOAuthClient {

    /**
     * 카카오 동의 화면 주소. 브라우저를 이리로 보낸다.
     *
     * @param state 이 왕복을 잇는 1회용 값. 카카오가 콜백에 그대로 돌려준다
     */
    String authorizationUri(OAuthState state);

    /**
     * 인가 코드를 액세스 토큰으로 교환한다.
     *
     * @param authorizationCode 카카오가 콜백에 실어 준 1회용 코드
     */
    String exchange(String authorizationCode);
}
