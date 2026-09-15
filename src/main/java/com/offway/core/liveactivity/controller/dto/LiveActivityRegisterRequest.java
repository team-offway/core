package com.offway.core.liveactivity.controller.dto;

import com.offway.core.liveactivity.domain.LiveActivityToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 잠금화면 카드의 갱신 주소 등록 요청(#575).
 *
 * <p><b>주인을 본문에 적지 않는다.</b> 적을 수 있으면 남의 코스로 등록해 그 사람의 여행 일정을 받아
 * 볼 수 있다. 주인은 access 토큰이 정한다(#280).
 *
 * @param courseId 잠금화면에 띄운 코스
 * @param pushToken 그 카드의 Live Activity push token
 */
public record LiveActivityRegisterRequest(
        @Schema(description = "잠금화면에 띄운 코스 id", example = "122") @NotNull @Positive Long courseId,
        @Schema(description = "Live Activity push token (hex)", example = "80a1b2c3...")
                @NotBlank
                @Size(max = LiveActivityToken.MAX_TOKEN_LENGTH)
                String pushToken) {
}
