package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.transport.domain.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 코스 생성 요청 — API 계약. 후보지역(추천)에서 지역을 고른 뒤 위저드 값(일수·밀도·이동수단·가는날)과 함께 넘어온다.
 *
 * @param regionId 코스를 만들 지역
 * @param travelDays 여행 일수(1~3, 최대 2박3일)
 * @param density 일정 밀도(PACKED 빡빡 / RELAXED 널널)
 * @param transport 이동수단(CAR·TRANSIT)
 * @param originLat 출발지 위도(동선 정렬 기준)
 * @param originLng 출발지 경도
 * @param travelDate 가는 날(정책 운영기간 매칭)
 */
public record CourseGenerateRequest(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long regionId,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @Min(1) @Max(Course.MAX_TRAVEL_DAYS) Integer travelDays,
        @Schema(example = "PACKED", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Density density,
        @Schema(example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TransportMode transport,
        @Schema(example = "37.49", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-90") @DecimalMax("90") Double originLat,
        @Schema(example = "127.02", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull @DecimalMin("-180") @DecimalMax("180") Double originLng,
        @Schema(example = "2026-05-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate travelDate) {

    public GenerateCourse toCommand() {
        return new GenerateCourse(regionId, travelDays, density, transport, originLat, originLng, travelDate);
    }
}
