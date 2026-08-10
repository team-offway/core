package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.LeaveDays;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.leave.service.dto.AddLeaveUsage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 연차 사용 내역 추가 요청.
 *
 * @param usedOn 연차를 쓴(또는 되돌린) 날 (필수)
 * @param days 증감 (필수, 0.5 단위). 사용은 양수, <b>취소는 음수</b>
 * @param reason 사유 (선택)
 * @param courseId 이 내역을 만든 코스 (선택 — 수동 입력이면 생략)
 */
public record AddLeaveUsageRequest(
        @Schema(description = "연차를 쓴 날", example = "2026-05-08", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull LocalDate usedOn,
        @Schema(description = "증감 (사용 양수 · 취소 음수, 0.5 단위)", example = "1.0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull Double days,
        @Schema(description = "사유 (선택)", example = "제주 여행") String reason,
        @Schema(description = "코스 ID (선택)", example = "12") Long courseId) {

    /** 값 계약을 검증하고 커맨드로 바꾼다. */
    public AddLeaveUsage toCommand() {
        if (!LeaveDays.isValidUsage(days)) {
            throw LeaveException.invalidLeaveUsageDays();
        }
        return AddLeaveUsage.manual(usedOn, days, reason, courseId);
    }
}
