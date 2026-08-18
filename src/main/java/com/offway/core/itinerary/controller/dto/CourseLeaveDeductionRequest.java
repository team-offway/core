package com.offway.core.itinerary.controller.dto;

import com.offway.core.leave.domain.StartDayLeave;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 코스 확정 시 연차 차감 요청(#91).
 *
 * <p><b>차감 일수를 받지 않는다.</b> 서버가 저장된 코스의 여행 날짜로 평일−공휴일을 다시 계산한다 — 클라이언트가
 * 보낸 값을 믿으면 임의 차감이 된다.
 *
 * <p><b>두 계약을 함께 받는다</b>(#138). 반반차가 생겨 {@code startDayLeave} 로 옮겼지만, 예전
 * {@code halfDayStart} 를 지금 끊으면 배포되는 순간 앱의 반차 선택이 조용히 종일로 바뀌어 연차가 0.5 더 깎인다.
 *
 * @param startDayLeave 첫날에 쓴 연차. 차감이 그 단위만큼 줄어든다(반차 0.5 · 반반차 0.25). 생략하면 종일
 * @param halfDayStart <b>예전 계약</b>. {@code startDayLeave} 가 오면 무시된다
 */
@Schema(description = "코스 확정 연차 차감 요청")
public record CourseLeaveDeductionRequest(
        @Schema(
                        description = "첫날에 쓴 연차 (생략 시 FULL_DAY). 차감이 그 단위만큼 줄어든다",
                        example = "HALF_DAY",
                        nullable = true)
                StartDayLeave startDayLeave,
        @Schema(
                        description = "첫날 반차 여부 — 예전 계약. startDayLeave 를 쓰면 보내지 않는다",
                        example = "false",
                        nullable = true,
                        deprecated = true)
                Boolean halfDayStart) {

    /**
     * 두 계약을 합류시킨다 — <b>새 필드가 이긴다</b>. 생략된 값은 종일로 굳혀 도메인·서비스가 null 을 다시
     * 판단하지 않게 경계에서 정한다.
     */
    public StartDayLeave startDayLeaveOrFullDay() {
        return startDayLeave != null ? startDayLeave : StartDayLeave.fromHalfDayFlag(halfDayStart);
    }
}
