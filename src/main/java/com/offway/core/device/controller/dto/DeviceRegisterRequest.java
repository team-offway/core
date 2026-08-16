package com.offway.core.device.controller.dto;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.service.dto.DeviceRegistration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 푸시 토큰 등록 요청(#264).
 *
 * @param token FCM 등록 토큰
 * @param platform 기기 종류
 */
public record DeviceRegisterRequest(
        @Schema(description = "FCM 등록 토큰", example = "fcm-token-abc123")
                @NotBlank
                @Size(max = DevicePushToken.MAX_TOKEN_LENGTH)
                String token,
        @Schema(description = "기기 종류", example = "IOS") @NotNull DevicePlatform platform) {

    public DeviceRegistration toRegistration(String guestId) {
        return new DeviceRegistration(guestId, token, platform);
    }
}
