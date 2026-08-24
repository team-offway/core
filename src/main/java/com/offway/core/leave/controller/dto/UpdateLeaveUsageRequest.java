package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.LeaveDays;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.leave.service.dto.UpdateLeaveUsage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 연차 사용 내역 수정 요청(#267) — <b>보낸 필드만 바꾼다</b>.
 *
 * <p><b>선택 필드라 전부 wrapper 다.</b> Jackson 3 은 primitive 자리에 값이 없으면 요청 전체를 400 으로
 * 되돌린다. 안 보낸 필드를 그대로 두는 것이 이 API 의 계약이므로 primitive 를 쓸 수 없다.
 *
 * <p><b>사유를 지우려면 빈 문자열을 보낸다.</b> 빠진 필드와 명시적 {@code null} 이 똑같이 {@code null} 로
 * 도착해 구분되지 않기 때문이다. 날짜·일수에는 이 문제가 없다 — 지울 수 있는 값이 아니라서, 지우고 싶으면
 * 내역 자체를 삭제한다.
 *
 * @param usedOn 새 사용일 (선택)
 * @param days 새 일수 (선택, 0.25 단위 양수)
 * @param reason 새 사유 (선택). 빈 문자열이면 사유를 지운다
 * @param memo 새 상세 메모 (선택, #319). 빈 문자열이면 메모를 지운다
 */
public record UpdateLeaveUsageRequest(
        @Schema(description = "새 사용일. 안 보내면 그대로", example = "2026-05-08") LocalDate usedOn,
        @Schema(description = "새 일수 (0.25 단위 양수). 안 보내면 그대로", example = "1.5") Double days,
        @Schema(description = "새 사유. 안 보내면 그대로, 빈 문자열이면 지운다", example = "제주 여행")
                String reason,
        @Schema(description = "새 상세 메모. 안 보내면 그대로, 빈 문자열이면 지운다", example = "숙소 변경됨")
                String memo) {

    /**
     * 값 계약을 검증하고 커맨드로 바꾼다.
     *
     * <p>등록과 <b>같은 규칙·같은 코드</b>로 거른다. 같은 값에 계약이 둘이면 화면이 어느 쪽을 따를지 알 수 없다.
     * 음수를 먼저 가르는 순서도 등록과 같다 — 사유가 다르면 코드도 달라야 화면이 "삭제로 취소하세요" 를
     * 안내할 수 있다(#276).
     */
    public UpdateLeaveUsage toCommand() {
        if (days != null) {
            if (LeaveDays.isReversal(days)) {
                throw LeaveException.leaveUsageReversalNotAllowed();
            }
            if (!LeaveDays.isValidUsage(days)) {
                throw LeaveException.invalidLeaveUsageDays();
            }
        }
        return UpdateLeaveUsage.builder()
                .usedOn(usedOn)
                .days(days)
                .reason(reason)
                .memo(memo)
                .build();
    }
}
