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

    /**
     * 이동수단 — <b>가용시간 산출에는 쓰지 않는다</b>(#289).
     *
     * <p>도달 한계 분(分)은 여행일수와 첫날 연차가 정하고, 수단은 그 시간에 얼마나 멀리 가는지에만 관여한다
     * (추천 단계의 {@code HaversineTravelTimeProvider}). 요청 계약에서 빼지 않은 것은 앱이 이미 보내고 있고,
     * 필수 필드를 없애면 계약이 깨지기 때문이다 — 다음 계약 변경 때 함께 정리한다.
     */
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
