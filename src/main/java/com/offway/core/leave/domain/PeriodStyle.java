package com.offway.core.leave.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 와이어프레임의 기간스타일 버튼 — 사용자가 날짜를 직접 고르지 않고 "어떤 모양의 여행"인지만 고른다. 각 상수가 <b>기준일에서
 * 가장 가까운 실제 구간</b>을 스스로 해석한다(결정 #38).
 *
 * <p>해석해서 날짜로 확정하는 이유는 그 뒤 계산이 날짜 직접선택과 완전히 같아지기 때문이다 — 공휴일이 구간에 끼면 연차가
 * 저절로 덜 빠지고(샌드위치 자동 반영), "명목값 vs 실제값" 분기가 사라진다.
 *
 * <p>분기를 {@code switch} 로 늘어놓지 않고 <b>상수별 메서드</b>로 둔다. 스타일이 늘면 여기 상수 하나만 추가되고 호출부
 * ({@link com.offway.core.leave.service.LeaveService})는 변하지 않는다.
 */
public enum PeriodStyle {

    /**
     * 당일치기 — 가장 가까운 <b>쉬는 날</b> 하루(주말 또는 공휴일).
     *
     * <p>기준일 당일로 잡지 않는다. 스타일 버튼은 "언제 갈지 서버가 정해줘" 라는 뜻이고, 하루짜리 여행에 굳이 연차를 태울
     * 이유가 없다. 기준일이 이미 쉬는 날이면 그날이다. 공휴일이 주말보다 먼저 오면 공휴일을 고른다 — 해석 단계에서
     * 공휴일을 보는 스타일은 이것뿐이다.
     */
    DAY_TRIP {
        @Override
        TripPeriod resolve(LocalDate baseDate, PeriodOptions options, Set<LocalDate> holidays) {
            LocalDate dayOff = search(baseDate, day -> isDayOff(day, holidays), "쉬는 날");
            return new TripPeriod(dayOff, dayOff);
        }
    },

    /**
     * 주말 포함 — 주말에 평일 하루를 붙인 2박 3일. 붙일 쪽은 {@link WeekendBridge} 가 정한다(금·토·일 또는 토·일·월).
     *
     * <p>공휴일을 보지 않는다. 시작 요일만으로 구간이 정해지고, 그 안에 공휴일이 끼면 연차가 덜 빠지는 것은 계산이 알아서
     * 한다({@link AvailableTime}).
     */
    WEEKEND {
        @Override
        TripPeriod resolve(LocalDate baseDate, PeriodOptions options, Set<LocalDate> holidays) {
            DayOfWeek startDayOfWeek = options.requiredWeekendBridge().startDayOfWeek();
            LocalDate start = search(baseDate, day -> day.getDayOfWeek() == startDayOfWeek, startDayOfWeek.name());
            return new TripPeriod(start, start.plusDays(WEEKEND_TRIP_DAYS - 1L));
        }
    },

    /**
     * 연차만 이어서 — 연속된 평일 N일(N은 보조 파라미터, 2~3).
     *
     * <p>구간에 주말이 끼지 않는 가장 이른 시작일을 고른다. 주말을 허용하면 "연차만 이어서" 가 아니게 되고, 붙이고 싶다면
     * 그게 {@link #WEEKEND} 다. 반면 <b>공휴일은 허용</b>한다 — 끼어 있으면 같은 일수에 연차가 덜 빠지는 이득이고,
     * 그걸 피할 이유가 없다.
     */
    CONNECTED {
        @Override
        TripPeriod resolve(LocalDate baseDate, PeriodOptions options, Set<LocalDate> holidays) {
            int days = options.requiredLeaveDays();
            LocalDate start = search(baseDate, day -> isWeekdayRun(day, days), "연속 평일 %d일".formatted(days));
            return new TripPeriod(start, start.plusDays(days - 1L));
        }
    };

    private static final Set<DayOfWeek> WEEKEND_DAYS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /** 주말 포함 스타일의 구간 길이 — 2박 3일(결정 #38). 여행 상한과 값이 같은 것은 우연이라 따로 둔다. */
    private static final int WEEKEND_TRIP_DAYS = 3;

    /**
     * 해석 탐색 상한(일). 어떤 조건이든 한 주 안에 반드시 만족되므로(주말·특정 요일은 7일 주기, 연속 평일 3일은 늦어도
     * 다음 월요일) 이 값에 닿는 것은 조건식이 틀렸다는 뜻이다 — 무한 루프 대신 즉시 드러나게 한다.
     */
    private static final int SEARCH_LIMIT_DAYS = 14;

    /**
     * 해석된 구간이 기준일로부터 벗어날 수 있는 최대 일수. 호출자가 <b>공휴일을 며칠 앞까지 모아둬야 하는지</b> 알아야
     * 하므로 계약으로 노출한다 — 창이 짧으면 해석된 구간에 걸친 공휴일이 조회에서 빠져 소모 연차가 과다 계산된다.
     */
    public static final int MAX_RESOLVE_OFFSET_DAYS = SEARCH_LIMIT_DAYS + AvailableTime.MAX_TRIP_DAYS;

    /**
     * 기준일부터 가장 가까운(같은 날 포함) 실제 구간으로 해석한다.
     *
     * @param baseDate 기준일 — 이 날 이후에서 찾는다
     * @param options 스타일별 보조 파라미터. 필요한 값이 없으면 계약 예외(400)
     * @param holidays 기준일 주변 공휴일 — {@link #DAY_TRIP} 만 쓴다
     */
    abstract TripPeriod resolve(LocalDate baseDate, PeriodOptions options, Set<LocalDate> holidays);

    /** 스타일 해석 진입점 — 입력 불변식을 한 곳에서 지키고 상수별 구현에 넘긴다. */
    public TripPeriod resolveFrom(LocalDate baseDate, PeriodOptions options, Set<LocalDate> holidays) {
        Objects.requireNonNull(baseDate, "baseDate 는 null 일 수 없습니다.");
        Objects.requireNonNull(options, "options 는 null 일 수 없습니다.");
        Objects.requireNonNull(holidays, "holidays 는 null 일 수 없습니다.");
        return resolve(baseDate, options, holidays);
    }

    /** 기준일부터 하루씩 나아가며 조건을 만족하는 첫 날. 상한에 닿으면 조건식 버그다(불변식). */
    private static LocalDate search(LocalDate baseDate, Predicate<LocalDate> condition, String what) {
        for (int offset = 0; offset < SEARCH_LIMIT_DAYS; offset++) {
            LocalDate candidate = baseDate.plusDays(offset);
            if (condition.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "%s 을 %d일 안에 찾지 못했습니다: baseDate=%s".formatted(what, SEARCH_LIMIT_DAYS, baseDate));
    }

    private static boolean isDayOff(LocalDate day, Set<LocalDate> holidays) {
        return WEEKEND_DAYS.contains(day.getDayOfWeek()) || holidays.contains(day);
    }

    /** 이 날부터 {@code days} 일이 모두 평일인가. 공휴일은 평일로 본다(연차만 덜 빠질 뿐 구간은 유효하다). */
    private static boolean isWeekdayRun(LocalDate start, int days) {
        for (int offset = 0; offset < days; offset++) {
            if (WEEKEND_DAYS.contains(start.plusDays(offset).getDayOfWeek())) {
                return false;
            }
        }
        return true;
    }
}
