package com.offway.core.liveactivity.controller.dto;

import com.offway.core.liveactivity.domain.PushToStartToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        @Schema(description = "APNs push-to-start 토큰 (hex, 짝수 길이)", example = "80a1b2c3")
                @NotBlank
                @Size(max = PushToStartToken.MAX_TOKEN_LENGTH)
                // 도메인의 규격과 **같은 상수**를 본다. 여기 정규식을 따로 적으면 한쪽만 고쳐졌을 때
                // 400 과 500 이 갈려, 같은 값이 입구에서는 통과하고 도메인에서 터진다.
                @Pattern(regexp = PushToStartToken.TOKEN_PATTERN, message = "hex 문자열이어야 합니다")
                String token) {
}
