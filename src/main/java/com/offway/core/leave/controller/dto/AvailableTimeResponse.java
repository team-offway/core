package com.offway.core.leave.controller.dto;

import com.offway.core.common.logging.LogSummary;
import com.offway.core.leave.service.dto.AvailableTimeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 가용시간(LNT) 산출 응답 — API 계약.
 *
 * <p>결정 #38 에 따라 "가용시간(시간 수)" 큰 숫자는 내리지 않는다. 클라이언트가 받는 것은 확정된 날짜·여행일수·소모 연차·
 * 편도 도달 한계다.
 *
 * <p><b>확정된 날짜를 함께 내린다.</b> 기간스타일 모드에서는 서버가 구간을 골랐으므로 클라이언트가 날짜를 모른다 — 화면에
 * "5월 8일~10일" 을 띄우려면 이 값이 필요하다. 날짜 직접 모드에서는 보낸 값의 확인이 된다.
 *
 * @param startDate 확정된 여행 시작일
 * @param endDate 확정된 여행 종료일
 * @param travelDays 여행 일수 (당일치기 1 · 1박2일 2 · 2박3일 3)
 * @param consumedLeaveDays 소모 연차 (평일−공휴일, 반차는 0.5)
 * @param maxReachMinutes 편도 도달 한계(분)
 */
public record AvailableTimeResponse(
        @Schema(description = "확정된 여행 시작일", example = "2026-05-08") LocalDate startDate,
        @Schema(description = "확정된 여행 종료일", example = "2026-05-10") LocalDate endDate,
        @Schema(description = "여행 일수 (1=당일치기 · 2=1박2일 · 3=2박3일)", example = "3") int travelDays,
        @Schema(description = "소모 연차 (평일−공휴일, 반차 0.5)", example = "1.0") double consumedLeaveDays,
        @Schema(description = "편도 도달 한계(분)", example = "420") int maxReachMinutes)
        implements LogSummary {

    private static final String LOG_FORMAT = "%s~%s travelDays=%d 소모연차=%.1f 도달한계=%d분";

    public static AvailableTimeResponse from(AvailableTimeResult result) {
        return new AvailableTimeResponse(
                result.period().startDate(),
                result.period().endDate(),
                result.availableTime().travelDays(),
                result.availableTime().consumedLeaveDays(),
                result.availableTime().maxReachMinutes());
    }

    @Override
    public String logSummary() {
        return LOG_FORMAT.formatted(startDate, endDate, travelDays, consumedLeaveDays, maxReachMinutes);
    }
}
