package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.PeriodOptions;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.leave.domain.PeriodStyle;
import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;

/**
 * 가용시간 산출 커맨드 — 서비스 내부용. 클라이언트가 <b>날짜를 직접 골랐는지 스타일을 골랐는지</b>를 타입으로 구분한다.
 *
 * <p>필드에 둘 다 담고 null 로 모드를 구분하면 "둘 다 있음"·"둘 다 없음" 같은 표현 불가능해야 할 상태가 타입에 남는다.
 * 닫힌 두 경우라 {@code sealed} + 패턴 매칭으로 두고, 어느 모드든 결국 {@link com.offway.core.leave.domain.TripPeriod}
 * 하나로 합류시킨다.
 */
public sealed interface AvailableTimeCommand {

    TransportMode transport();

    StartDayLeave startDayLeave();

    /**
     * 날짜를 직접 고른 경우 — 해석 없이 그 구간으로 계산한다.
     *
     * @param startDate 여행 시작일
     * @param endDate 여행 종료일
     */
    record FixedDates(
            LocalDate startDate, LocalDate endDate, TransportMode transport, StartDayLeave startDayLeave)
            implements AvailableTimeCommand {}

    /**
     * 기간스타일을 고른 경우 — 기준일에서 가장 가까운 실제 구간으로 해석한 뒤 계산한다.
     *
     * @param style 기간스타일
     * @param baseDate 해석 기준일 (이 날 이후에서 구간을 찾는다)
     * @param options 스타일별 보조 파라미터
     */
    record FromStyle(
            PeriodStyle style,
            LocalDate baseDate,
            PeriodOptions options,
            TransportMode transport,
            StartDayLeave startDayLeave)
            implements AvailableTimeCommand {}
}
