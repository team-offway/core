package com.offway.core.itinerary.controller.dto;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.service.dto.MyCourses;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

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
        long courseId,
        long regionId,
        @Schema(description = "여행 시작일 (저장 시 넣지 않았으면 null)", example = "2026-08-14", nullable = true)
                LocalDate travelDate,
        @Schema(
                        description = "오늘 기준 남은 날. 오늘이면 0, 지난 여행이면 음수. 여행 날짜가 없으면 null",
                        example = "14",
                        nullable = true)
                Integer dDay,
        int travelDays,
        @Schema(example = "PACKED") String density,
        int placeCount,
        @Schema(description = "이 코스로 연차를 차감했는가", example = "true") boolean leaveDeducted) {

    public static CourseSummaryResponse from(Course course, MyCourses myCourses) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getRegionId(),
                course.getTravelDate(),
                myCourses.dDay(course),
                course.getTravelDays(),
                course.getDensity().name(),
                course.totalSlots(),
                myCourses.isDeducted(course));
    }

    /** 목록 전체를 한 번에 — 차감 여부·D-day 는 코스마다 다시 묻지 않고 {@link MyCourses} 가 이미 들고 있다. */
    public static List<CourseSummaryResponse> listFrom(MyCourses myCourses) {
        return myCourses.courses().stream().map(course -> from(course, myCourses)).toList();
    }
}
