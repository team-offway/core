package com.offway.core.user.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.offway.core.user.service.dto.IssuedToken;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발급된 토큰 쌍.
 *
 * @param accessToken 이후 요청에 {@code Authorization: Bearer} 로 싣는다
 * @param refreshToken access 만료 시 재발급에 쓴다. 재발급하면 이 값은 폐기되고 새 값이 내려온다
 * @param expiresIn access 토큰 잔여 수명(초)
 * @param isNewUser 이 로그인이 가입이었는지. 앱은 {@code true} 면 온보딩(잔여 연차 입력), {@code false} 면 홈으로
 *     보낸다. 재발급 응답에서는 항상 {@code false} 다 — 재발급은 가입일 수 없다
 */
public record TokenResponse(
        @Schema(description = "access 토큰") String accessToken,
        @Schema(description = "refresh 토큰(재발급 시 회전됨)") String refreshToken,
        @Schema(description = "access 토큰 수명(초)", example = "3600") long expiresIn,
        // 이름을 못박는다. record 접근자 isNewUser() 는 bean 규약으로 읽으면 속성명이 newUser 라, 그대로 두면
        // 직렬화 이름이 Jackson 설정에 좌우된다. FE 계약이 isNewUser 이므로 흔들릴 여지를 없앤다.
        @JsonProperty("isNewUser")
                @Schema(description = "이번 로그인이 가입이었는지 — true 면 온보딩으로 보낸다", example = "true")
                boolean isNewUser) {

    public static TokenResponse from(IssuedToken token) {
        return new TokenResponse(
                token.accessToken(), token.refreshToken(), token.expiresInSeconds(), token.newUser());
    }
}
