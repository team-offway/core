package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.leave.service.dto.CreateAvailableTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 가용시간(LNT) 산출 요청 — API 계약.
 *
 * <p>확정된 날짜 구간을 받는다. "당일치기/주말포함/연차이어서" 같은 기간스타일을 날짜로 해석하는 일은 상위 계층(FE 또는 후속 #46)의 몫이고, 이
 * 엔드포인트는 확정된 {@code startDate}~{@code endDate} 로 계산만 한다.
 *
 * @param startDate 여행 시작일 (필수)
 * @param endDate 여행 종료일 (필수, 시작일과 같거나 이후)
 * @param transport 이동수단 (필수)
 * @param halfDayStart 출발일 반차 여부 (선택, 기본 false)
 */
public record AvailableTimeRequest(
        @Schema(description = "여행 시작일", example = "2026-05-06", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull LocalDate startDate,
        @Schema(description = "여행 종료일", example = "2026-05-08", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull LocalDate endDate,
        @Schema(description = "이동수단", example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull TransportMode transport,
        @Schema(description = "출발일 반차 여부 (선택, 기본 false)", example = "false") Boolean halfDayStart) {

    /**
     * 커맨드로 변환하며 <b>날짜 구간 계약을 검증</b>한다.
     *
     * <p>순서 역전·상한 초과는 멀쩡한 클라이언트가 정상 요청으로 닿을 수 있는 계약 위반이라 여기서 400 으로 막는다. 여기서 걸러야 도메인
     * ({@link AvailableTime})의 같은 검사는 진짜 불변식 안전망(500)으로 남는다.
     */
    public CreateAvailableTime toCommand() {
        if (endDate.isBefore(startDate)) {
            throw LeaveException.invalidDateRange();
        }
        long span = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        if (span > AvailableTime.MAX_TRIP_DAYS) {
            throw LeaveException.tripTooLong();
        }
        boolean halfDay = Boolean.TRUE.equals(halfDayStart); // 선택 필드 — 부재/null 은 반차 아님
        return new CreateAvailableTime(startDate, endDate, transport, halfDay);
    }
}
