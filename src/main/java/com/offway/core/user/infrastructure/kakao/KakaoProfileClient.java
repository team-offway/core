package com.offway.core.user.infrastructure.kakao;

/**
 * 카카오 프로필 조회 port.
 *
 * <p>Kakao 액세스 토큰에는 신원 정보가 없어, 토큰만으로는 누구인지 알 수 없다. 이 port 가 그 한 번의 외부 호출을 감싼다.
 */
public interface KakaoProfileClient {

    /**
     * 액세스 토큰의 주인을 조회한다.
     *
     * @param accessToken 앱이 카카오 SDK 에서 받아 넘긴 액세스 토큰
     * @throws com.offway.core.user.domain.UserException 토큰이 무효({@code USER-001})거나 카카오를 부르지 못했을 때
     *     ({@code USER-005})
     */
    KakaoProfile fetchProfile(String accessToken);

    /**
     * 액세스 토큰을 <b>발급한 앱</b>이 어디인지 조회한다.
     *
     * <p>프로필 조회로는 답할 수 없는 질문이라 호출이 따로 필요하다. 이 값을 확인하지 않으면 남의 카카오 앱에서
     * 발급된 토큰이 우리 로그인을 통과한다({@link KakaoTokenInfo} 참고).
     *
     * @param accessToken 앱이 카카오 SDK 에서 받아 넘긴 액세스 토큰
     * @throws com.offway.core.user.domain.UserException 토큰이 무효({@code USER-001})거나 카카오를 부르지 못했을 때
     *     ({@code USER-005})
     */
    KakaoTokenInfo fetchTokenInfo(String accessToken);
}
