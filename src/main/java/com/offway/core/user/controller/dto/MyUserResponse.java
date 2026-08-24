package com.offway.core.user.controller.dto;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.service.dto.MyUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 내 정보(#282).
 *
 * <p><b>비어 있을 수 있는 필드를 null 로 내린다.</b> 빈 문자열로 채우면 앱이 "동의를 안 해서 없다" 와
 * "값이 빈 문자열이다" 를 구분할 수 없어, 마이페이지에 빈 줄을 그린다.
 *
 * <p>{@code isNewUser} 는 넣지 않는다 — 그건 "이번 로그인이 가입이었나" 라서 조회에는 뜻이 없다. 온보딩
 * 분기는 로그인 응답이 소유한다.
 *
 * @param nickname 표시 이름. 항상 있다 — 값이 안 왔으면 가입 때 기본값이 채워진다
 * @param email 이메일. 카카오는 동의를 안 하면 없고, Apple 은 최초 로그인에서만 준다
 * @param provider 어느 provider 로 로그인했는지. local 개발 로그인 계정은 없다
 * @param profileImageUrl 프로필 사진 주소(#308). Apple 은 사진을 주지 않고, Kakao 는 동의를 거부하거나 기본
 *     이미지를 쓰면 없다. 앱은 없으면 기본 아이콘을 그린다
 * @param joinedAt 가입 시각(KST)
 */
public record MyUserResponse(
        @Schema(example = "세빈") String nickname,
        @Schema(example = "user@example.com", nullable = true) String email,
        @Schema(example = "GOOGLE", nullable = true) AuthProvider provider,
        @Schema(example = "https://k.kakaocdn.net/dn/abc/profile.jpg", nullable = true) String profileImageUrl,
        @Schema(example = "2026-08-17T21:30:00") LocalDateTime joinedAt) {

    /** 화면에 그대로 쓰는 값이라 서비스 시간대로 바꿔 내린다 — 다른 응답의 시각과 같은 기준이어야 한다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static MyUserResponse from(MyUser myUser) {
        return new MyUserResponse(
                myUser.nickname(),
                myUser.email(),
                myUser.provider(),
                myUser.profileImageUrl(),
                LocalDateTime.ofInstant(myUser.joinedAt(), SERVICE_ZONE));
    }
}
