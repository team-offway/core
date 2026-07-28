package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 코스 목록 항목 — API 계약. 카드 리스트용 요약(상세는 {@code GET /courses/{id}}).
 *
 * @param courseId 코스 ID
 * @param regionId 지역 ID
 * @param travelDays 여행 일수
 * @param density 일정 밀도
 * @param placeCount 전체 장소 수
 */
public record CourseSummaryResponse(
        long courseId, long regionId, int travelDays, @Schema(example = "PACKED") String density, int placeCount) {

    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.getId(), course.getRegionId(), course.getTravelDays(),
                course.getDensity().name(), course.totalSlots());
    }
}
