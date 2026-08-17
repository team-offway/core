package com.offway.core.user.infrastructure.kakao;

import java.util.List;

/**
 * 카카오 {@code /v1/user/access_token_info} 응답에서 우리가 쓰는 것만 추린 결과.
 *
 * <p><b>이 호출이 있어야 "우리 앱 토큰인가" 에 답할 수 있다.</b> 프로필 조회({@code /v2/user/me})는 토큰의 주인이
 * 누구인지만 알려줄 뿐, 그 토큰을 <b>어느 앱이 발급했는지</b>는 알려주지 않는다. 그래서 프로필 응답만 믿으면 다른
 * 카카오 앱에서 발급된 액세스 토큰을 그대로 우리 서버에 던져 그 사용자로 로그인할 수 있다 — Apple·Google 에서
 * {@code aud} 가 막는 바로 그 자리다.
 *
 * <p><b>{@code id} 로 교차 확인은 하지 않는다.</b> 프로필과 토큰 정보를 <b>같은 액세스 토큰</b>으로 잇달아 묻기
 * 때문에 두 응답의 회원번호가 갈릴 경로가 없다. 반대로 강제하면 카카오가 이 필드를 빠뜨린 응답 하나에 전체 로그인이
 * 401 이 된다 — 막는 공격은 없고 새 실패 경로만 생긴다. 우리 앱 토큰인지는 {@code appId} 가 판정한다.
 *
 * @param id 카카오 회원번호. 지금은 쓰지 않고 로그·추후 교차 확인을 위해 보관한다
 * @param appId 이 토큰을 발급한 카카오 앱의 번호. 우리 앱 번호와 같아야 한다
 */
public record KakaoTokenInfo(String id, String appId) {

    /** 이 토큰이 주어진 앱 번호 중 하나에서 발급됐는지. */
    public boolean issuedByAnyOf(List<String> allowedAppIds) {
        return appId != null && allowedAppIds.contains(appId);
    }
}
