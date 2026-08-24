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
 * @param usedDays 쓴 연차. <b>0 아래로 내려가지 않는다</b> — 옛 상쇄 등록(음수 행)이 남아 있으면 {@code usages}
 *     의 합보다 클 수 있다(#265). 클라이언트는 목록을 더해 검산하지 말고 이 값을 쓴다
 * @param remainingDays 남은 연차. <b>총 연차를 넘지 않고</b>(#265), 초과 사용 시 <b>음수일 수 있다</b>(결정 #38)
 * @param usages 사용 내역 (최근 순)
 */
public record MyLeaveResponse(
        @Schema(description = "총 연차", example = "15.0") double totalDays,
        @Schema(description = "쓴 연차 (0 이상 — 옛 음수 행이 있으면 목록 합과 다를 수 있다)", example = "2.0")
                double usedDays,
        @Schema(description = "남은 연차 (총 연차 이하 · 초과 사용 시 음수)", example = "13.0") double remainingDays,
        List<Usage> usages) {

    public static MyLeaveResponse from(MyLeave myLeave) {
        return new MyLeaveResponse(
                myLeave.summary().totalDays(),
                myLeave.summary().usedDays(),
                myLeave.summary().remainingDays(),
                myLeave.usages().stream().map(Usage::from).toList());
    }

    /**
     * @param id 내역 ID — 삭제({@code DELETE /me/usages/{id}})의 대상이다
     * @param usedOn 연차를 쓴 날
     * @param days 쓴 일수. 새 내역은 양수지만, <b>삭제 API 가 없던 시절의 상쇄 등록은 음수로 남아 있다</b>(#265)
     * @param reason 사유 (없으면 null)
     * @param memo 상세 메모 (없으면 null, #319)
     * @param courseId 이 내역을 만든 코스 (수동 입력이면 null). <b>값이 있으면 삭제할 수 없다</b> — 코스에서 차감을 취소한다
     */
    public record Usage(
            long id,
            @Schema(example = "2026-05-08") LocalDate usedOn,
            @Schema(description = "쓴 일수 (옛 상쇄 등록만 음수)", example = "1.0") double days,
            @Schema(example = "제주 여행", nullable = true) String reason,
            @Schema(example = "숙소 체크인 15시", nullable = true) String memo,
            @Schema(nullable = true) Long courseId) {

        static Usage from(LeaveUsage usage) {
            return new Usage(
                    usage.getId(),
                    usage.getUsedOn(),
                    usage.getDays(),
                    usage.getReason(),
                    usage.getMemo(),
                    usage.getCourseId());
        }
    }
}
