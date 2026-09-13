package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.LeaveDays;
import com.offway.core.leave.domain.LeaveException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 총 연차 수정 요청 — 와이어프레임의 +/- 스테퍼. 가운데 숫자를 직접 입력해 1.25 같은 조합도 넣는다(결정 #38, #278).
 *
 * <p><b>사용만 0.25 로 열면 잔여가 안 맞는다.</b> 총 15일에서 0.25씩 세 번 쓰면 잔여가 14.25 인데, 총량을
 * 14.25 로 맞추려는 순간 막혀 사용자가 장부를 정리할 방법이 없어진다. 두 곳이 같은 격자를 써야 한다.
 *
 * <p><b>상한(99일)은 여기서 못 본다</b>(#558). 화면이 받는 값은 총이 아니라 잔여라, 앱은 이 필드에
 * {@code 사용자가 입력한 잔여 + 사용분} 을 실어 보낸다. 그래서 상한은 총이 아니라 잔여에 걸려야 하는데
 * 이 경계는 사용분을 모른다 — {@code MyLeaveService} 가 잰다.
 *
 * @param totalDays 총 연차 (0.25 단위, 0 이상)
 */
public record UpdateMyLeaveRequest(
        @Schema(
                        description =
                                """
                                총 연차. 0.25 단위(반반차)의 0 이상 값이다.
                                상한 99일은 총이 아니라 남은 연차(총 - 사용분)에 걸리므로, 사용분이 쌓인 만큼 총은 99 를 넘을 수 있다.""",
                        example = "15.0",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull Double totalDays) {

    /**
     * 값 계약을 검증한다. 여기서 걸러야 도메인의 같은 검사가 진짜 불변식 안전망(500)으로 남는다.
     *
     * <p>상한은 재지 않는다 — 사용분을 알아야 하는 검사라 서비스가 맡는다(#558).
     */
    public double validTotalDays() {
        if (!LeaveDays.isValidTotal(totalDays)) {
            throw LeaveException.invalidTotalLeaveDays();
        }
        return totalDays;
    }
}
