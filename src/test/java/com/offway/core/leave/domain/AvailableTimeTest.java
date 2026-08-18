package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.transport.domain.TransportMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AvailableTimeTest {

    private static final Set<LocalDate> NO_HOLIDAY = Set.of();

    /** 요일 하드코딩을 피하려 월요일을 앵커로 고정한다. MON=+0 … FRI=+4 · SAT=+5 · SUN=+6. */
    private static final LocalDate MON = LocalDate.of(2026, 5, 4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @ParameterizedTest
    @CsvSource({
        // 시작일, 종료일, 기대 travelDays
        "2026-05-01, 2026-05-01, 1", // 당일치기
        "2026-05-01, 2026-05-02, 2", // 1박2일
        "2026-05-01, 2026-05-03, 3", // 2박3일
    })
    void 날짜_구간_길이로_여행일수를_분류한다(LocalDate start, LocalDate end, int expectedDays) {
        AvailableTime time = AvailableTime.of(start, end, NO_HOLIDAY, TransportMode.CAR);

        assertEquals(expectedDays, time.travelDays());
    }

    @ParameterizedTest
    @CsvSource({
        // 종료일(=여행일수 결정), 이동수단, 기대 도달한계(분)
        "2026-05-01, CAR, 120",     // 당일치기 자차 2h
        "2026-05-02, CAR, 240",     // 1박2일 자차 4h
        "2026-05-03, CAR, 420",     // 2박3일 자차 7h
        "2026-05-01, TRANSIT, 84",  // 당일치기 대중교통 2h×0.7
        "2026-05-02, TRANSIT, 168", // 1박2일 대중교통 4h×0.7
        "2026-05-03, TRANSIT, 294", // 2박3일 대중교통 7h×0.7
    })
    void 여행일수와_이동수단으로_도달한계를_산출한다(LocalDate end, TransportMode transport, int expectedMinutes) {
        LocalDate start = LocalDate.of(2026, 5, 1);

        AvailableTime time = AvailableTime.of(start, end, NO_HOLIDAY, transport);

        assertEquals(expectedMinutes, time.maxReachMinutes());
    }

    @Test
    void 시작일이_종료일보다_늦으면_불변식_위반이다() {
        LocalDate start = LocalDate.of(2026, 5, 3);
        LocalDate end = LocalDate.of(2026, 5, 1);

        assertThrows(IllegalArgumentException.class,
                () -> AvailableTime.of(start, end, NO_HOLIDAY, TransportMode.CAR));
    }

    @Test
    void 여행일수가_상한_2박3일을_넘으면_불변식_위반이다() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 4); // 3박4일

        assertThrows(IllegalArgumentException.class,
                () -> AvailableTime.of(start, end, NO_HOLIDAY, TransportMode.CAR));
    }

    @Test
    void 시작일이_null이면_거부한다() {
        assertThrows(NullPointerException.class,
                () -> AvailableTime.of(null, LocalDate.of(2026, 5, 1), NO_HOLIDAY, TransportMode.CAR));
    }

    @Test
    void 이동수단이_null이면_거부한다() {
        LocalDate date = LocalDate.of(2026, 5, 1);

        assertThrows(NullPointerException.class,
                () -> AvailableTime.of(date, date, NO_HOLIDAY, null));
    }

    @Test
    void 평일_3일은_연차_3일을_소모한다() {
        AvailableTime time = AvailableTime.of(MON, MON.plusDays(2), NO_HOLIDAY, TransportMode.CAR); // 월~수

        assertEquals(3.0, time.consumedLeaveDays(), 0.001);
    }

    @Test
    void 주말은_연차를_소모하지_않는다() {
        AvailableTime time = AvailableTime.of(MON.plusDays(4), MON.plusDays(6), NO_HOLIDAY, TransportMode.CAR); // 금~일

        assertEquals(1.0, time.consumedLeaveDays(), 0.001); // 금만 연차, 토·일 무료
    }

    @Test
    void 구간에_낀_공휴일은_연차를_소모하지_않는다() {
        Set<LocalDate> holidays = Set.of(MON.plusDays(1)); // 화요일이 공휴일

        AvailableTime time = AvailableTime.of(MON, MON.plusDays(2), holidays, TransportMode.CAR); // 월~수

        assertEquals(2.0, time.consumedLeaveDays(), 0.001); // 월·수만 연차
    }

    @Test
    void 평일_당일치기는_연차_1일을_소모한다() {
        AvailableTime time = AvailableTime.of(MON, MON, NO_HOLIDAY, TransportMode.CAR);

        assertEquals(1.0, time.consumedLeaveDays(), 0.001);
    }

    @Test
    void 주말_당일치기는_연차를_소모하지_않는다() {
        AvailableTime time = AvailableTime.of(MON.plusDays(5), MON.plusDays(5), NO_HOLIDAY, TransportMode.CAR); // 토요일

        assertEquals(0.0, time.consumedLeaveDays(), 0.001);
    }

    @Test
    void 구간_전체가_공휴일이면_연차를_소모하지_않는다() {
        Set<LocalDate> holidays = Set.of(MON, MON.plusDays(1), MON.plusDays(2)); // 월·화·수 모두 공휴일

        AvailableTime time = AvailableTime.of(MON, MON.plusDays(2), holidays, TransportMode.CAR);

        assertEquals(0.0, time.consumedLeaveDays(), 0.001);
    }

    @ParameterizedTest
    @CsvSource({"FULL_DAY, 1.0", "HALF_DAY, 0.5", "QUARTER_DAY, 0.25"})
    void 첫날은_쓴_단위만큼만_소모한다(StartDayLeave startDayLeave, double expected) {
        // 평일 당일치기 — 첫날이 곧 전부라 단위가 그대로 소모량이 된다.
        AvailableTime time = AvailableTime.of(MON, MON, NO_HOLIDAY, TransportMode.CAR, startDayLeave);

        assertEquals(expected, time.consumedLeaveDays(), 0.001);
    }

    @ParameterizedTest
    @CsvSource({"FULL_DAY, 3.0", "HALF_DAY, 2.5", "QUARTER_DAY, 2.25"})
    void 첫날_단위는_첫날에만_적용된다(StartDayLeave startDayLeave, double expected) {
        // 월(단위) + 화·수(각 1). 둘째 날부터 깎이면 하루를 두 번 아끼는 셈이 된다.
        AvailableTime time = AvailableTime.of(MON, MON.plusDays(2), NO_HOLIDAY, TransportMode.CAR, startDayLeave);

        assertEquals(expected, time.consumedLeaveDays(), 0.001);
    }

    @ParameterizedTest
    @EnumSource(StartDayLeave.class)
    void 출발일이_주말이면_어느_단위든_소모가_없다(StartDayLeave startDayLeave) {
        // 주말은 애초에 연차를 안 쓴다 — 단위를 곱해 음수·소수가 되면 안 된다.
        AvailableTime time =
                AvailableTime.of(MON.plusDays(5), MON.plusDays(5), NO_HOLIDAY, TransportMode.CAR, startDayLeave);

        assertEquals(0.0, time.consumedLeaveDays(), 0.001);
    }

    @Test
    void 단위를_안_주면_종일과_같다() {
        // 4-arg 오버로드가 기본값을 쓴다 — 안 보내던 호출부가 지금과 같은 결과를 받아야 한다.
        AvailableTime without = AvailableTime.of(MON, MON.plusDays(2), NO_HOLIDAY, TransportMode.CAR);
        AvailableTime explicit =
                AvailableTime.of(MON, MON.plusDays(2), NO_HOLIDAY, TransportMode.CAR, StartDayLeave.FULL_DAY);

        assertEquals(without.consumedLeaveDays(), explicit.consumedLeaveDays(), 0.001);
    }
}
