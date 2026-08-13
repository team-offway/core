package com.offway.core.itinerary.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 코스 확정 시 연차 차감 요청(#91).
 *
 * <p><b>차감 일수를 받지 않는다.</b> 서버가 저장된 코스의 여행 날짜로 평일−공휴일을 다시 계산한다 — 클라이언트가
 * 보낸 값을 믿으면 임의 차감이 된다.
 *
 * @param halfDayStart 첫날을 반차로 시작하는지. 차감이 0.5 줄어든다. 생략하면 종일로 본다.
 */
@Schema(description = "코스 확정 연차 차감 요청")
public record CourseLeaveDeductionRequest(
        @Schema(description = "첫날 반차 여부 (생략 시 종일)", example = "false", nullable = true)
                Boolean halfDayStart) {

    /** 생략된 값을 종일로 굳힌다 — 도메인·서비스가 null 을 다시 판단하지 않게 경계에서 정한다. */
    public boolean halfDayStartOrFullDay() {
        return Boolean.TRUE.equals(halfDayStart);
    }
}
