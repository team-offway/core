package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;

/**
 * 카카오 {@code /v2/user/me} 응답에서 우리가 쓰는 것만 추린 결과.
 *
 * @param id 카카오 회원번호. provider 안에서 유일하고 변하지 않아 계정 매칭 키로 쓴다
 * @param nickname 프로필 닉네임. 동의를 거부하면 비어 있다
 * @param email 카카오 계정 이메일. 동의를 거부하면 비어 있다
 * @param profileImageUrl 프로필 사진 주소(#308). 동의를 거부하거나 기본 이미지를 쓰면 비어 있다.
 *     {@code thumbnail_image_url}(목록용 작은 이미지)이 아니라 원본을 쓴다 — 마이 화면의 큰 자리에 들어간다
 */
public record KakaoProfile(String id, String nickname, String email, String profileImageUrl) {

    public SocialIdentity toSocialIdentity() {
        // 정규 생성자를 쓴다 — audience 자리에 무엇이 들어가는지(카카오는 ID 토큰을 안 써 없다) 눈에 보이게.
        return new SocialIdentity(AuthProvider.KAKAO, id, nickname, email, null, profileImageUrl);
    }
}
