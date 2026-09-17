package com.offway.core.liveactivity.controller.dto;

import com.offway.core.liveactivity.domain.PushToStartToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * push-to-start 토큰 등록 요청(#583).
 *
 * <p><b>주인을 본문에 적지 않는다.</b> 적을 수 있으면 남의 기기에 카드를 띄울 수 있다. 주인은
 * access 토큰이 정한다(#280).
 *
 * @param token {@code Activity.pushToStartTokenUpdates} 가 준 hex 토큰. 기기 단위이고 코스와 무관하다
 */
public record PushToStartRegisterRequest(
        @Schema(description = "APNs push-to-start 토큰 (hex)", example = "80a1b2c3...")
                @NotBlank
                @Size(max = PushToStartToken.MAX_TOKEN_LENGTH)
                String token) {
}
