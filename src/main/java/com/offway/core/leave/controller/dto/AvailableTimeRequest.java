package com.offway.core.leave.controller.dto;

import com.offway.core.leave.domain.AvailableTime;
import com.offway.core.leave.domain.LeaveException;
import com.offway.core.leave.domain.PeriodOptions;
import com.offway.core.leave.domain.PeriodStyle;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.leave.domain.WeekendBridge;
import com.offway.core.leave.service.dto.AvailableTimeCommand;
import com.offway.core.transport.domain.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 가용시간(LNT) 산출 요청 — API 계약. <b>두 가지 모드 중 하나</b>로 여행 구간을 지정한다.
 *
 * <ul>
 *   <li><b>날짜 직접</b> — {@code startDate} + {@code endDate}. 사용자가 캘린더에서 고른 경우.
 *   <li><b>기간스타일</b> — {@code periodStyle} + {@code baseDate}(+ 스타일별 보조 파라미터). 와이어프레임의
 *       "당일치기 / 주말 포함 / 연차이어서" 버튼. 서버가 기준일에서 가장 가까운 실제 구간을 해석해 확정한다.
 * </ul>
 *
 * <p>어느 모드든 응답은 <b>확정된 날짜</b>를 함께 내려주므로, 클라이언트는 스타일만 골라도 며칠에 가는지 알 수 있다.
 *
 * @param startDate 여행 시작일 (날짜 직접 모드 — 종료일과 함께 필수)
 * @param endDate 여행 종료일 (날짜 직접 모드 — 시작일과 함께 필수, 시작일과 같거나 이후)
 * @param periodStyle 기간스타일 (기간스타일 모드 — 필수)
 * @param baseDate 해석 기준일 (기간스타일 모드 — 필수)
 * @param weekendBridge 주말에 붙일 평일 하루 ({@link PeriodStyle#WEEKEND} 에 필수)
 * @param leaveDays 이어서 쓸 연차 일수 2~3 ({@link PeriodStyle#CONNECTED} 에 필수)
 * @param transport 이동수단 (필수)
 * @param startDayLeave 첫날에 쓴 연차 (선택, 기본 FULL_DAY). 출발 시각이 이 값에서 도출된다
 * @param halfDayStart <b>예전 계약</b> (선택). {@code startDayLeave} 가 오면 무시된다
 */
public record AvailableTimeRequest(
        @Schema(description = "여행 시작일 (날짜 직접 모드)", example = "2026-05-06") LocalDate startDate,
        @Schema(description = "여행 종료일 (날짜 직접 모드)", example = "2026-05-08") LocalDate endDate,
        @Schema(description = "기간스타일 (기간스타일 모드)", example = "WEEKEND") PeriodStyle periodStyle,
        @Schema(description = "해석 기준일 (기간스타일 모드)", example = "2026-05-04") LocalDate baseDate,
        @Schema(description = "주말에 붙일 평일 하루 (WEEKEND 에 필수)", example = "FRIDAY")
                WeekendBridge weekendBridge,
        @Schema(description = "이어서 쓸 연차 일수 2~3 (CONNECTED 에 필수)", example = "3") Integer leaveDays,
        @Schema(description = "이동수단", example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull TransportMode transport,
        @Schema(
                        description = "첫날에 쓴 연차 (선택, 기본 FULL_DAY). 출발 시각이 여기서 도출된다 "
                                + "— FULL_DAY 08시 · HALF_DAY 12시 · QUARTER_DAY 15시",
                        example = "HALF_DAY",
                        nullable = true)
                StartDayLeave startDayLeave,
        @Schema(
                        description = "출발일 반차 여부 — 예전 계약. startDayLeave 를 쓰면 보내지 않는다",
                        example = "false",
                        nullable = true,
                        deprecated = true)
                Boolean halfDayStart) {

    /**
     * 커맨드로 변환하며 <b>모드 계약을 검증</b>한다. 스타일별 보조 파라미터 검증은 {@link PeriodOptions} 가 소유한다 —
     * "이 스타일에 무엇이 필요한지" 는 스타일 쪽 지식이라 여기 흩어놓지 않는다.
     *
     * <p>여기서 걸러야 도메인({@link AvailableTime}·{@link com.offway.core.leave.domain.TripPeriod})의 같은 검사가
     * 진짜 불변식 안전망(500)으로 남는다.
     */
    public AvailableTimeCommand toCommand() {
        boolean anyDate = startDate != null || endDate != null;
        boolean bothDates = startDate != null && endDate != null;
        boolean choseStyle = periodStyle != null;

        // 각 모드가 '완전히' 성립하는지 따로 본다. 두 조건을 XOR 로만 보면 "날짜 한쪽 + 스타일" 이
        // 스타일 모드로 흘러가 클라이언트가 보낸 날짜가 <b>조용히 버려진다</b>.
        // 날짜 모드는 둘 다 있어야 하고(한쪽만이면 나머지를 서버가 임의로 정하게 된다),
        // 스타일 모드는 날짜가 하나도 없어야 한다(있으면 무엇을 쓸지 요청이 스스로 모순된다).
        boolean datesMode = bothDates && !choseStyle;
        boolean styleMode = choseStyle && !anyDate;
        if (!datesMode && !styleMode) {
            throw LeaveException.ambiguousPeriodInput();
        }
        StartDayLeave leave = resolveStartDayLeave();

        return styleMode ? fromStyle(leave) : fixedDates(leave);
    }

    /**
     * 두 계약을 합류시킨다 — <b>새 필드가 이긴다</b>.
     *
     * <p>앱이 갈아타는 동안 예전 {@code halfDayStart} 도 받는다. 그것만 끊으면 배포되는 순간 지금 앱의 반차
     * 선택이 조용히 종일로 바뀌는데, 사용자는 반차를 골랐다고 믿는다.
     *
     * <p>둘 다 왔을 때 새 필드를 택하는 이유: 예전 필드는 반반차를 표현할 수 없어, 그쪽을 우선하면 새 값이
     * 표현하려던 것을 잃는다. 어긋난 조합(예: {@code QUARTER_DAY} + {@code halfDayStart:true})을 거절하지는
     * 않는다 — 전환기에 둘을 함께 보내는 클라이언트가 생기고, 그때 400 을 주면 로그인처럼 화면이 막힌다.
     */
    private StartDayLeave resolveStartDayLeave() {
        return startDayLeave != null ? startDayLeave : StartDayLeave.fromHalfDayFlag(halfDayStart);
    }

    private AvailableTimeCommand fromStyle(StartDayLeave startDayLeave) {
        if (baseDate == null) {
            // 서버 시계로 대체하지 않는다 — 자정 근처에서 클라이언트가 보는 '오늘' 과 하루 어긋난다.
            throw LeaveException.baseDateRequired();
        }
        PeriodOptions options = new PeriodOptions(weekendBridge, leaveDays);
        return new AvailableTimeCommand.FromStyle(periodStyle, baseDate, options, transport, startDayLeave);
    }

    private AvailableTimeCommand fixedDates(StartDayLeave startDayLeave) {
        if (endDate.isBefore(startDate)) {
            throw LeaveException.invalidDateRange();
        }
        long span = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        if (span > AvailableTime.MAX_TRIP_DAYS) {
            throw LeaveException.tripTooLong();
        }
        return new AvailableTimeCommand.FixedDates(startDate, endDate, transport, startDayLeave);
    }
}
