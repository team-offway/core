package com.offway.core.user.controller.dto;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.service.dto.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 로그인 요청 — 앱이 provider SDK 로 받아 온 ID 토큰을 넘긴다.
 *
 * @param provider GOOGLE · KAKAO · APPLE
 * @param idToken provider SDK 가 준 OIDC ID 토큰
 * @param nickname 표시 이름(선택). Apple 은 ID 토큰에 이름을 담지 않고 최초 인증 응답에만 주므로, 앱이 그때 받아
 *     넘기지 않으면 애플 사용자는 이름을 갖지 못한다
 */
public record LoginRequest(
        @NotNull @Schema(description = "OAuth provider", example = "APPLE") AuthProvider provider,
        @NotBlank @Schema(description = "provider SDK 가 발급한 ID 토큰") String idToken,
        @Schema(description = "표시 이름(선택). Apple 최초 로그인에서만 값이 온다", example = "세빈", nullable = true)
                String nickname) {

    public LoginCommand toCommand() {
        return new LoginCommand(provider, idToken, nickname);
    }
}
