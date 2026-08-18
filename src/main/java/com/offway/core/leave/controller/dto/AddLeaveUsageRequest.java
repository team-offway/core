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
 * @param usedOn 연차를 쓴 날 (필수)
 * @param days 쓴 일수 (필수, 0.25 단위 <b>양수</b>). 되돌리려면 등록이 아니라 삭제다(#265 의 삭제 API, #276)
 * @param reason 사유 (선택)
 * @param courseId 이 내역을 만든 코스 (선택 — 수동 입력이면 생략)
 */
public record AddLeaveUsageRequest(
        @Schema(description = "연차를 쓴 날", example = "2026-05-08", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull LocalDate usedOn,
        @Schema(description = "쓴 일수 (0.25 단위 양수 — 반반차 0.25 · 반차 0.5). 되돌리려면 내역 삭제 API 를 쓴다",
                        example = "1.0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull Double days,
        @Schema(description = "사유 (선택)", example = "제주 여행") String reason,
        @Schema(description = "코스 ID (선택)", example = "12") Long courseId) {

    /**
     * 값 계약을 검증하고 커맨드로 바꾼다.
     *
     * <p>음수를 먼저 가른다 — 사유가 다르면 코드도 달라야 화면이 "삭제로 취소하세요" 를 안내할 수 있다(#276).
     */
    public AddLeaveUsage toCommand() {
        if (LeaveDays.isReversal(days)) {
            throw LeaveException.leaveUsageReversalNotAllowed();
        }
        if (!LeaveDays.isValidUsage(days)) {
            throw LeaveException.invalidLeaveUsageDays();
        }
        return AddLeaveUsage.manual(usedOn, days, reason, courseId);
    }
}
