package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.domain.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Set;

/**
 * 코스 재생성 요청(#114) — "이 코스 말고 다른 걸로".
 *
 * <p>생성과 같은 조건에 <b>전부 선택인</b> 세 가지를 더한다. 아무것도 안 주면 무작위로 다시 짠다.
 *
 * @param seed 씨앗을 직접 고를 때. <b>재현이 목적</b>이라 이 값을 주면 서버가 씨앗을 바꿔가며 시도하지 않는다
 * @param previousSeed 지금 화면에 떠 있는 코스의 씨앗. 이것과 다른 코스를 만들려고 쓴다
 * @param excludePoiContentIds 빼고 짤 장소들 — "이 장소 말고"
 */
@Schema(description = "코스 재생성 요청")
public record CourseRegenerateRequest(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long regionId,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @Min(1) @Max(Course.MAX_TRAVEL_DAYS) Integer travelDays,
        @Schema(example = "PACKED", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Density density,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @Schema(example = "37.49", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @Schema(example = "127.02", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @Schema(example = "2026-05-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate travelDate,
        @Schema(description = """
                이 수단으로 코스를 짠다(#453). 카드에서 수단 칩을 눌렀을 때만 보낸다.

                **도착 지점이 바뀌면 동선도 바뀐다** — 코스는 집이 아니라 내린 곳에서 시작하므로,
                시간표만 갈아끼우면 순서와 첫날 시각이 어긋난 채 남는다. 지역에 따라 역과 터미널이
                수십 km 떨어져 있다(양양은 강릉역까지 42km).

                그 지역에 그 수단이 안 닿으면 **서버가 고른 수단으로 돌아간다.**""",
                example = "TRAIN", nullable = true)
                TransitMode transitMode,
        @Schema(
                        description = "첫날에 쓴 연차 (선택, 기본 FULL_DAY). 출발 시각이 여기서 나오고 그 시각이 "
                                + "첫날 일정을 자른다 — FULL_DAY 08시 · HALF_DAY 12시 · QUARTER_DAY 15시",
                        example = "HALF_DAY",
                        nullable = true)
                StartDayLeave startDayLeave,
        @Schema(description = "씨앗을 직접 고를 때. 같은 씨앗이면 같은 코스가 나온다", nullable = true) Long seed,
        @Schema(description = "지금 보고 있는 코스의 씨앗. 생략하면 첫 생성 코스로 본다", nullable = true)
                Long previousSeed,
        @Schema(description = "빼고 짤 장소들 — \"이 장소 말고\"", nullable = true)
                Set<String> excludePoiContentIds) {

    /** 재생성이 시도마다 씨앗만 바꿔 끼우므로, 여기서는 씨앗을 비워 둔 커맨드를 만든다. */
    public GenerateCourse toCommand() {
        return GenerateCourse.builder()
                .regionId(regionId)
                .travelDays(travelDays)
                .density(density)
                .transport(transport)
                .transitMode(transitMode)
                .originLat(originLat)
                .originLng(originLng)
                .travelDate(travelDate)
                .startDayLeave(startDayLeave)
                .seed(GenerateCourse.FIRST_SEED)
                .excludePoiContentIds(excludePoiContentIds)
                .build();
    }
}
