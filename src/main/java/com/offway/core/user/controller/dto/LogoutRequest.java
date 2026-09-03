package com.offway.core.user.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그아웃 요청 — <b>어느 세션을 끊을지</b>(#389).
 *
 * <p>누구인지는 access 토큰이 답하고, 이 본문은 <b>그 사람의 어느 기기인지</b>만 답한다.
 *
 * <p>본문 전체가 선택이다. 이 필드를 안 싣는 옛 앱은 예전처럼 모든 기기가 끊기고, 실은 앱은 그 기기만
 * 끊긴다. {@code @NotBlank} 를 붙이지 않는 이유가 그것이다 — 붙이면 옛 앱의 로그아웃이 400 이 된다.
 *
 * @param refreshToken 이 기기가 들고 있는 refresh 토큰 원문. 없으면 이 사용자의 모든 세션을 끊는다
 */
public record LogoutRequest(
        @Schema(description = "이 기기의 refresh 토큰 원문. 비우면 모든 기기에서 로그아웃된다", nullable = true)
                String refreshToken) {

    /** 본문이 통째로 없을 때와 필드만 없을 때를 호출부가 같게 다루도록. */
    public static String refreshTokenOrNull(LogoutRequest request) {
        return request == null ? null : request.refreshToken();
    }
}
