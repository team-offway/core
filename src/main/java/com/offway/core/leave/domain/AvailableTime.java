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
 * @param consumedLeaveDays 소모 연차. 구간의 평일에서 공휴일을 뺀 값 (첫날은 쓴 단위만큼 — 반차 0.5 · 반반차 0.25)
 */
public record AvailableTime(int travelDays, int maxReachMinutes, double consumedLeaveDays) {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /** 평일 하루가 소모하는 연차. */
    private static final double FULL_DAY_LEAVE = 1.0;

    /** 여행 상한 — 코스 생성이 Day1~3 만 지원한다(결정 #38). 열려면 이 상수와 코스 생성을 함께 확장한다. */
    public static final int MAX_TRIP_DAYS = 3;

    /**
     * 여행일수별 도달 한계(분). index = travelDays. 당일 2h · 1박2일 3h · 2박3일 4h.
     *
     * <p><b>이동수단은 여기 관여하지 않는다</b>(#289). 이 값은 "이동에 쓸 수 있는 시간" 이고, 그 시간에
     * 얼마나 멀리 가는지는 수단의 평균속도가 정한다({@code HaversineTravelTimeProvider}). 예전에는
     * {@code TransportMode} 가 이 분 예산까지 0.7 로 깎아 대중교통이 <b>감쇠를 두 번</b> 받았다 —
     * 당일 서울 출발이 89곳 중 3곳까지 줄었다.
     *
     * <p><b>2박3일이 7h 였을 때는 필터가 아니었다.</b> 서울 최원거리가 완도 363분이라 420분은 89곳을
     * 하나도 거르지 못했고, 2박3일을 고르면 거리 조건이 사실상 사라졌다. 4h 로 내려 55곳이 된다.
     * 1박2일도 함께 내린다 — 안 내리면 두 등급이 240 으로 같아져 구분이 없어진다.
     */
    private static final int[] BASE_REACH_MINUTES = {0, 120, 180, 240};

    /**
     * 확정된 날짜 구간에서 가용 정보를 만든다.
     *
     * @param start 여행 시작일
     * @param end 여행 종료일
     * @param holidays 구간에 걸치는 공휴일 (소모 연차 계산용)
     */
    public static AvailableTime of(LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        return of(start, end, holidays, StartDayLeave.DEFAULT);
    }

    /**
     * 첫날에 쓴 연차를 반영해 가용 정보를 만든다.
     *
     * @param startDayLeave 첫날에 쓴 연차. 소모 연차(반차 0.5 · 반반차 0.25)와 <b>첫날 도달 상한</b>을 함께
     *     정한다 — 늦게 떠나면 그날 갈 수 있는 거리가 준다(#289)
     */
    public static AvailableTime of(
            LocalDate start,
            LocalDate end,
            Set<LocalDate> holidays,
            StartDayLeave startDayLeave) {
        Objects.requireNonNull(start, "start 는 null 일 수 없습니다.");
        Objects.requireNonNull(end, "end 는 null 일 수 없습니다.");
        Objects.requireNonNull(holidays, "holidays 는 null 일 수 없습니다.");
        Objects.requireNonNull(startDayLeave, "startDayLeave 는 null 일 수 없습니다.");

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

        // 두 축의 작은 쪽을 따른다 — 여행일수는 "얼마나 멀리", 첫날 연차는 "언제까지 도착" 을 답한다(#289).
        int reach = Math.min(BASE_REACH_MINUTES[days], startDayLeave.firstDayReachMinutes());
        double leave = countLeaveDays(start, end, holidays, startDayLeave);
        return new AvailableTime(days, reach, leave);
    }

    /**
     * 구간의 평일에서 공휴일을 뺀 소모 연차. 주말·공휴일은 무료(결정 #38).
     *
     * <p>반차·반반차는 <b>출발일이 평일일 때 그날만</b> 그 단위로 센다(창 안 대체). "목금토일" 처럼 여행 창 밖으로 반나절
     * 앞당기는 조기출발 반차(+0.5)는 이 값객체가 아니라 서비스가 창을 정한 뒤 더한다.
     *
     * <p>단위별 값을 여기서 분기하지 않는다 — {@link StartDayLeave} 가 자기 소모량을 들고 있어, 단위가 늘어도
     * 이 메서드는 그대로다.
     */
    private static double countLeaveDays(
            LocalDate start, LocalDate end, Set<LocalDate> holidays, StartDayLeave startDayLeave) {
        double leave = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (WEEKEND.contains(day.getDayOfWeek()) || holidays.contains(day)) {
                continue;
            }
            leave += day.isEqual(start) ? startDayLeave.consumedLeave() : FULL_DAY_LEAVE;
        }
        return leave;
    }
}
