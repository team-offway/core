package com.offway.core.leave.service.dto;

import java.time.LocalDate;

/**
 * 연차 사용 내역 추가 커맨드 — 서비스 내부용.
 *
 * @param usedOn 연차를 쓴 날
 * @param days 쓴 일수(0.5 단위 양수). 되돌리는 것은 등록이 아니라 삭제다(#265 의 삭제 API, #276)
 * @param reason 사유 (선택)
 * @param courseId 이 내역을 만든 코스 (수동 입력이면 null)
 * @param halfDayStart 첫날 반차 여부. 코스 차감에서만 뜻이 있고, 날짜를 고칠 때 차감량을 다시 계산하는 입력이라 남긴다(#170)
 */
public record AddLeaveUsage(LocalDate usedOn, double days, String reason, Long courseId, boolean halfDayStart) {

    /** 사용자가 직접 남기는 내역 — 반차 개념이 없다. */
    public static AddLeaveUsage manual(LocalDate usedOn, double days, String reason, Long courseId) {
        return new AddLeaveUsage(usedOn, days, reason, courseId, false);
    }
}
