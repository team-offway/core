package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;

/**
 * 카카오 {@code /v2/user/me} 응답에서 우리가 쓰는 것만 추린 결과.
 *
 * @param id 카카오 회원번호. provider 안에서 유일하고 변하지 않아 계정 매칭 키로 쓴다
 * @param nickname 프로필 닉네임. 동의를 거부하면 비어 있다
 * @param email 카카오 계정 이메일. 동의를 거부하면 비어 있다
 */
public record KakaoProfile(String id, String nickname, String email) {

    public SocialIdentity toSocialIdentity() {
        return new SocialIdentity(AuthProvider.KAKAO, id, nickname, email);
    }
}
