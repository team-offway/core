package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.AvailableTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가용시간(LNT) 산출 응답 — API 계약.
 *
 * <p>결정 #38 에 따라 "가용시간(시간 수)" 큰 숫자는 내리지 않는다. 클라이언트가 받는 것은 여행일수·소모 연차·편도 도달 한계다.
 *
 * @param travelDays 여행 일수 (당일치기 1 · 1박2일 2 · 2박3일 3)
 * @param consumedLeaveDays 소모 연차 (평일−공휴일, 반차는 0.5)
 * @param maxReachMinutes 편도 도달 한계(분)
 */
public record AvailableTimeResponse(
        @Schema(description = "여행 일수 (1=당일치기 · 2=1박2일 · 3=2박3일)", example = "3") int travelDays,
        @Schema(description = "소모 연차 (평일−공휴일, 반차 0.5)", example = "3.0") double consumedLeaveDays,
        @Schema(description = "편도 도달 한계(분)", example = "420") int maxReachMinutes) {

    public static AvailableTimeResponse from(AvailableTime availableTime) {
        return new AvailableTimeResponse(
                availableTime.travelDays(), availableTime.consumedLeaveDays(), availableTime.maxReachMinutes());
    }
}
