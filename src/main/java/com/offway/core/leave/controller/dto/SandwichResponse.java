package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.SandwichHoliday;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 샌드위치 연휴 추천 응답 — API 계약.
 *
 * @param items 추천 연휴 목록 (효율 높은 순)
 */
public record SandwichResponse(List<Item> items) {

    public static SandwichResponse from(List<SandwichHoliday> sandwiches) {
        return new SandwichResponse(sandwiches.stream().map(Item::from).toList());
    }

    /**
     * @param leaveDates 연차로 소모하는 평일들 (징검다리)
     * @param totalRestDays 총 연속 휴식 일수
     * @param efficiency 효율 문구 ("연차일수=휴식일수" 형태, 예 "1일=5일")
     * @param window 연속 휴식 구간
     */
    public record Item(
            @Schema(description = "연차로 소모하는 평일", example = "[\"2026-05-04\"]") List<LocalDate> leaveDates,
            @Schema(description = "총 연속 휴식 일수", example = "5") int totalRestDays,
            @Schema(description = "효율 (연차 1일 = 휴식 N일)", example = "1일=5일") String efficiency,
            Window window) {

        private static final String EFFICIENCY_FORMAT = "%d일=%d일";

        public static Item from(SandwichHoliday sandwich) {
            return new Item(
                    sandwich.leaveDates(),
                    sandwich.totalRestDays(),
                    EFFICIENCY_FORMAT.formatted(sandwich.leaveDays(), sandwich.totalRestDays()),
                    new Window(sandwich.windowStart(), sandwich.windowEnd()));
        }
    }

    /**
     * @param start 휴식 시작일
     * @param end 휴식 종료일
     */
    public record Window(
            @Schema(description = "휴식 시작일", example = "2026-05-01") LocalDate start,
            @Schema(description = "휴식 종료일", example = "2026-05-05") LocalDate end) {}
}
