package com.offway.core.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 재발급 요청.
 *
 * @param refreshToken 로그인·직전 재발급 때 받은 refresh 토큰 원문
 */
public record ReissueRequest(
        @NotBlank @Schema(description = "refresh 토큰 원문") String refreshToken) {}
