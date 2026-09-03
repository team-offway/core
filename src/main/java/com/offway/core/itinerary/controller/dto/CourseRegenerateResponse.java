package com.offway.core.itinerary.controller.dto;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.itinerary.service.dto.RegeneratedCourse;
import com.offway.core.common.response.Attributed;
import com.offway.core.common.response.DataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

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
        @Schema(description = "직전 코스와 겹친 볼거리 비율 (맛집·숙소는 후보가 적어 세지 않는다)", example = "0.25") double overlapRatio)
        implements Attributed {

    /**
     * 감싼 코스의 출처를 <b>그대로 물려받는다</b>(#399).
     *
     * <p>이 응답은 코스를 안에 넣고 재생성 정보만 얹은 것이다. 여기서 안 물려받으면 <b>재생성 화면에서만
     * 출처가 사라진다</b> — 같은 코스인데 어떻게 받았느냐에 따라 표기가 달라지는 셈이다.
     */
    @Override
    public Set<DataSource> sources() {
        return course.sources();
    }

    public static CourseRegenerateResponse from(RegeneratedCourse regenerated, List<CuratedLink> curatedLinks) {
        return new CourseRegenerateResponse(
                CourseResponse.from(regenerated.course(), curatedLinks),
                regenerated.seed(),
                regenerated.differentFromPrevious(),
                regenerated.overlapRatio());
    }
}
