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
 * @param id 카카오 회원번호. 프로필 조회 결과와 교차 확인해 같은 사람인지 본다
 * @param appId 이 토큰을 발급한 카카오 앱의 번호. 우리 앱 번호와 같아야 한다
 */
public record KakaoTokenInfo(String id, String appId) {

    /** 이 토큰이 주어진 앱 번호 중 하나에서 발급됐는지. */
    public boolean issuedByAnyOf(List<String> allowedAppIds) {
        return appId != null && allowedAppIds.contains(appId);
    }
}
