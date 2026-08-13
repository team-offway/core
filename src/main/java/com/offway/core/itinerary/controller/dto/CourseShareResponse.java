package com.offway.core.itinerary.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공유 링크 발급 응답(#261) — 담지 않고 링크만 만들었을 때.
 *
 * <p><b>토큰 하나만 준다.</b> 화면은 방금 보고 있던 코스를 그대로 들고 있어 코스 내용을 되돌려줄 이유가 없고,
 * 붙여 보내려면 혜택·날씨를 다시 조립해야 해서 외부 호출까지 딸려온다.
 *
 * @param shareToken 공유 토큰. 공유 URL 은 {@code /c/{shareToken}}
 */
public record CourseShareResponse(
        @Schema(example = "a1B2c3D4e5F6g7H8i9J0kL") String shareToken) {

    public static CourseShareResponse from(String shareToken) {
        return new CourseShareResponse(shareToken);
    }
}
