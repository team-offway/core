package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.service.dto.RegeneratedCourse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 코스 재생성 응답(#114) — 새 코스와, 그것이 <b>정말 달라졌는지</b>.
 *
 * <p>{@code differentFromPrevious} 가 거짓이면 후보가 모자라 더 다르게 만들지 못한 것이다. 이걸 안 내리고 조용히
 * 같은 코스를 주면 사용자는 버튼이 고장 난 줄 안다.
 *
 * @param seed 이 코스를 만든 씨앗. 그대로 다시 주면 같은 코스가 나온다
 * @param overlapRatio 직전 코스와 겹친 <b>볼거리</b> 비율(0~1). 맛집·숙소는 후보가 적어 대개 그대로라 세지 않는다
 */
public record CourseRegenerateResponse(
        CourseResponse course,
        @Schema(description = "이 코스를 만든 씨앗 (다음 재생성에 previousSeed 로 넘긴다)", example = "8123456789")
                long seed,
        @Schema(description = "직전 코스와 충분히 달라졌는가. 거짓이면 후보가 모자란 지역이다", example = "true")
                boolean differentFromPrevious,
        @Schema(description = "직전 코스와 겹친 볼거리 비율 (맛집·숙소는 후보가 적어 세지 않는다)", example = "0.25") double overlapRatio) {

    public static CourseRegenerateResponse from(RegeneratedCourse regenerated) {
        return new CourseRegenerateResponse(
                CourseResponse.from(regenerated.course()),
                regenerated.seed(),
                regenerated.differentFromPrevious(),
                regenerated.overlapRatio());
    }
}
