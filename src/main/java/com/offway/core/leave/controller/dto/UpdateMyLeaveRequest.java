package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.LeaveDays;
import com.offway.core.leave.domain.LeaveException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 총 연차 수정 요청 — 와이어프레임의 +/- 스테퍼. 가운데 숫자를 직접 입력해 1.5 같은 반차 조합도 넣는다(결정 #38).
 *
 * @param totalDays 총 연차 (0.5 단위, 0~99)
 */
public record UpdateMyLeaveRequest(
        @Schema(
                        description = "총 연차. 0.5 단위(반차)이고 0~99 범위다. 화면 안내와 같은 범위를 서버도 강제한다.",
                        example = "15.0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull Double totalDays) {

    /** 값 계약을 검증한다. 여기서 걸러야 도메인의 같은 검사가 진짜 불변식 안전망(500)으로 남는다. */
    public double validTotalDays() {
        if (!LeaveDays.isValidTotal(totalDays)) {
            throw LeaveException.invalidTotalLeaveDays();
        }
        return totalDays;
    }
}
