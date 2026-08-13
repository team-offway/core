package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.LeaveUsage;
import com.offway.core.leave.service.dto.MyLeave;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * "내 연차" 응답 — API 계약. 남은 연차는 서버가 계산해 내려준다(클라이언트가 빼지 않게).
 *
 * @param totalDays 총 연차
 * @param usedDays 쓴 연차 (증감 합 — 취소가 있으면 줄어든다)
 * @param remainingDays 남은 연차. <b>음수일 수 있다</b> — 초과 사용을 서버가 막지 않기 때문이다(결정 #38)
 * @param usages 사용 내역 (최근 순)
 */
public record MyLeaveResponse(
        @Schema(description = "총 연차", example = "15.0") double totalDays,
        @Schema(description = "쓴 연차(증감 합)", example = "2.0") double usedDays,
        @Schema(description = "남은 연차 (초과 사용 시 음수)", example = "13.0") double remainingDays,
        List<Usage> usages) {

    public static MyLeaveResponse from(MyLeave myLeave) {
        return new MyLeaveResponse(
                myLeave.summary().totalDays(),
                myLeave.summary().usedDays(),
                myLeave.summary().remainingDays(),
                myLeave.usages().stream().map(Usage::from).toList());
    }

    /**
     * @param id 내역 ID
     * @param usedOn 연차를 쓴(또는 되돌린) 날
     * @param days 증감 — 사용은 양수, 취소는 음수
     * @param reason 사유 (없으면 null)
     * @param courseId 이 내역을 만든 코스 (수동 입력이면 null)
     */
    public record Usage(
            long id,
            @Schema(example = "2026-05-08") LocalDate usedOn,
            @Schema(description = "증감 (사용 양수 · 취소 음수)", example = "1.0") double days,
            @Schema(example = "제주 여행", nullable = true) String reason,
            @Schema(nullable = true) Long courseId) {

        static Usage from(LeaveUsage usage) {
            return new Usage(
                    usage.getId(), usage.getUsedOn(), usage.getDays(), usage.getReason(), usage.getCourseId());
        }
    }
}
