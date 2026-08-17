package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.StartDayLeave;
import java.time.LocalDate;

/**
 * 연차 사용 내역 추가 커맨드 — 서비스 내부용.
 *
 * @param usedOn 연차를 쓴(또는 되돌린) 날
 * @param days 증감(0.5 단위). 사용은 양수, 취소는 음수 — 되돌리는 것은 등록이 아니라 삭제가 맞고, 음수 거절은
 *     앱이 갈아탄 뒤로 미뤘다(#276)
 * @param reason 사유 (선택)
 * @param courseId 이 내역을 만든 코스 (수동 입력이면 null)
 * @param startDayLeave 첫날에 쓴 연차. 코스 차감에서만 뜻이 있고, 날짜를 고칠 때 차감량을 다시 계산하는
 *     입력이라 함께 남긴다(#170)
 */
public record AddLeaveUsage(
        LocalDate usedOn, double days, String reason, Long courseId, StartDayLeave startDayLeave) {

    /** 사용자가 직접 남기는 내역 — 첫날 단위 개념이 없어 종일로 둔다. */
    public static AddLeaveUsage manual(LocalDate usedOn, double days, String reason, Long courseId) {
        return new AddLeaveUsage(usedOn, days, reason, courseId, StartDayLeave.DEFAULT);
    }
}
