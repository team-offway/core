package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.StartDayLeave;
import lombok.Builder;
import java.time.LocalDate;

/**
 * 연차 사용 내역 추가 커맨드 — 서비스 내부용.
 *
 * @param usedOn 연차를 쓴 날
 * @param days 쓴 일수(0.25 단위 양수). 되돌리는 것은 등록이 아니라 삭제다(#265 의 삭제 API, #276)
 * @param reason 사유 (선택)
 * @param memo 상세 메모 (선택) — 사유와 다른 칸이다(#319)
 * @param courseId 이 내역을 만든 코스 (수동 입력이면 null)
 * @param startDayLeave 첫날에 쓴 연차. 코스 차감에서만 뜻이 있고, 날짜를 고칠 때 차감량을 다시 계산하는
 *     입력이라 함께 남긴다(#170)
 */
@Builder
public record AddLeaveUsage(
        LocalDate usedOn,
        double days,
        String reason,
        String memo,
        Long courseId,
        StartDayLeave startDayLeave) {

    /** 사용자가 직접 남기는 내역 — 첫날 단위 개념이 없어 종일로 둔다. */
    public static AddLeaveUsage manual(
            LocalDate usedOn, double days, String reason, String memo, Long courseId) {
        return AddLeaveUsage.builder()
                .usedOn(usedOn)
                .days(days)
                .reason(reason)
                .memo(memo)
                .courseId(courseId)
                .startDayLeave(StartDayLeave.DEFAULT)
                .build();
    }
}
