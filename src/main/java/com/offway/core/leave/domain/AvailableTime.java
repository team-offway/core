package com.offway.core.leave.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 확정된 여행 구간에서 산출한 가용 정보 (LNT).
 *
 * <p>큰 "가용시간(시간 수)" 숫자는 쓰지 않는다(결정 #38). 이 값객체가 소유하는 것은 여행일수·이동 한계다.
 *
 * @param travelDays 여행 일수. 당일치기(1) · 1박2일(2) · 2박3일(3)
 * @param maxReachMinutes 편도 도달 한계(분)
 * @param consumedLeaveDays 소모 연차. 구간의 평일에서 공휴일을 뺀 값 (반차는 0.5 — 결정 #38)
 */
public record AvailableTime(int travelDays, int maxReachMinutes, double consumedLeaveDays) {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /** 평일 하루가 소모하는 연차. */
    private static final double FULL_DAY_LEAVE = 1.0;

    /** 반차가 소모하는 연차. */
    private static final double HALF_DAY_LEAVE = 0.5;

    /** 여행 상한 — 코스 생성이 Day1~3 만 지원한다(결정 #38). 열려면 이 상수와 코스 생성을 함께 확장한다. */
    public static final int MAX_TRIP_DAYS = 3;

    /**
     * 여행일수별 자차 기준 도달 한계(분). index = travelDays.
     *
     * <p>당일 2h · 1박2일 4h · 2박3일 7h. 튜닝 전제의 초기값이다(결정 #38). 이동수단 배율은 {@link TransportMode} 가 적용한다.
     */
    private static final int[] BASE_REACH_MINUTES = {0, 120, 240, 420};

    /**
     * 확정된 날짜 구간에서 가용 정보를 만든다.
     *
     * @param start 여행 시작일
     * @param end 여행 종료일
     * @param holidays 구간에 걸치는 공휴일 (소모 연차 계산용)
     * @param transport 이동수단
     */
    public static AvailableTime of(LocalDate start, LocalDate end, Set<LocalDate> holidays, TransportMode transport) {
        return of(start, end, holidays, transport, false);
    }

    /**
     * 출발일 반차를 반영해 가용 정보를 만든다.
     *
     * @param halfDayStart 출발일을 반차로 쓰는가. 참이고 출발일이 평일이면 그날 연차를 0.5 만 소모한다.
     */
    public static AvailableTime of(
            LocalDate start, LocalDate end, Set<LocalDate> holidays, TransportMode transport, boolean halfDayStart) {
        Objects.requireNonNull(start, "start 는 null 일 수 없습니다.");
        Objects.requireNonNull(end, "end 는 null 일 수 없습니다.");
        Objects.requireNonNull(holidays, "holidays 는 null 일 수 없습니다.");
        Objects.requireNonNull(transport, "transport 는 null 일 수 없습니다.");

        // 불변식 — 유효한 구간은 상위(요청 DTO·스타일 해석)가 보장한다. 여기 닿는 위반은 버그다.
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("종료일이 시작일보다 앞섭니다: start=%s end=%s".formatted(start, end));
        }
        // 상한 검사는 int 캐스팅 전에 long 으로 한다 — 먼저 캐스팅하면 극단적 날짜 차이가
        // 랩어라운드돼 상한 검사를 우연히 통과할 수 있다. 여기서 걸러야 아래 캐스팅이 안전하다.
        long span = end.toEpochDay() - start.toEpochDay() + 1;
        if (span > MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("여행일수가 상한(%d)을 넘습니다: %d일".formatted(MAX_TRIP_DAYS, span));
        }
        int days = (int) span;

        int reach = transport.applyReach(BASE_REACH_MINUTES[days]);
        double leave = countLeaveDays(start, end, holidays, halfDayStart);
        return new AvailableTime(days, reach, leave);
    }

    /**
     * 구간의 평일에서 공휴일을 뺀 소모 연차. 주말·공휴일은 무료(결정 #38).
     *
     * <p>반차는 <b>출발일이 평일일 때 그날을 0.5 로</b> 센다(창 안 대체). "목금토일" 처럼 여행 창 밖으로 반나절 앞당기는 조기출발
     * 반차(+0.5)는 이 값객체가 아니라 서비스가 창을 정한 뒤 더한다.
     */
    private static double countLeaveDays(
            LocalDate start, LocalDate end, Set<LocalDate> holidays, boolean halfDayStart) {
        double leave = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (WEEKEND.contains(day.getDayOfWeek()) || holidays.contains(day)) {
                continue;
            }
            boolean isHalfDay = halfDayStart && day.isEqual(start);
            leave += isHalfDay ? HALF_DAY_LEAVE : FULL_DAY_LEAVE;
        }
        return leave;
    }
}
