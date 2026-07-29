package com.offway.core.user.controller.dto;

import com.offway.core.user.service.dto.IssuedToken;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발급된 토큰 쌍.
 *
 * @param accessToken 이후 요청에 {@code Authorization: Bearer} 로 싣는다
 * @param refreshToken access 만료 시 재발급에 쓴다. 재발급하면 이 값은 폐기되고 새 값이 내려온다
 * @param expiresIn access 토큰 잔여 수명(초)
 */
public record TokenResponse(
        @Schema(description = "access 토큰") String accessToken,
        @Schema(description = "refresh 토큰(재발급 시 회전됨)") String refreshToken,
        @Schema(description = "access 토큰 수명(초)", example = "3600") long expiresIn) {

    public static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.accessToken(), token.refreshToken(), token.expiresInSeconds());
    }
}
