package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 기간스타일 해석 단위 테스트.
 *
 * <p>날짜는 2026-05 을 기준으로 쓴다 — 05-04(월) · 05-05(화, 어린이날) · 05-06(수) · 05-07(목) · 05-08(금) ·
 * 05-09(토) · 05-10(일) · 05-11(월). 요일이 분기의 전부라서 달력을 고정해두고 경계를 훑는다.
 */
class PeriodStyleTest {

    private static final Set<LocalDate> NO_HOLIDAY = Set.of();
    private static final LocalDate CHILDRENS_DAY = LocalDate.of(2026, 5, 5);

    private static LocalDate date(int day) {
        return LocalDate.of(2026, 5, day);
    }

    /**
     * @param baseDate 기준일
     * @param expectedStart 해석된 시작일
     * @param expectedEnd 해석된 종료일
     * @param what 시나리오 설명(실패 메시지·테스트 이름용)
     */
    private record Case(LocalDate baseDate, LocalDate expectedStart, LocalDate expectedEnd, String what) {

        @Override
        public String toString() {
            return what;
        }
    }

    // ── 당일치기 ────────────────────────────────────────────────

    private static List<Case> dayTripCases() {
        return List.of(
                new Case(date(4), date(9), date(9), "월요일 기준 → 다가오는 토요일"),
                new Case(date(8), date(9), date(9), "금요일 기준 → 바로 다음날 토요일"),
                new Case(date(9), date(9), date(9), "토요일 기준 → 그날 (같은 날 포함)"),
                new Case(date(10), date(10), date(10), "일요일 기준 → 그날"),
                new Case(date(11), date(16), date(16), "일요일 지난 월요일 → 그 주 토요일"));
    }

    @ParameterizedTest
    @MethodSource("dayTripCases")
    void 당일치기는_가장_가까운_쉬는_날_하루로_해석한다(Case testCase) {
        TripPeriod period = PeriodStyle.DAY_TRIP.resolveFrom(testCase.baseDate(), PeriodOptions.none(), NO_HOLIDAY);

        assertEquals(testCase.expectedStart(), period.startDate(), testCase.what());
        assertEquals(testCase.expectedEnd(), period.endDate(), testCase.what());
        assertEquals(1, period.days(), "당일치기는 하루다");
    }

    @Test
    void 당일치기는_주말보다_먼저_오는_공휴일을_고른다() {
        // 05-04(월) 기준 — 주말(05-09)보다 어린이날(05-05)이 먼저다.
        TripPeriod period =
                PeriodStyle.DAY_TRIP.resolveFrom(date(4), PeriodOptions.none(), Set.of(CHILDRENS_DAY));

        assertEquals(CHILDRENS_DAY, period.startDate());
        assertEquals(CHILDRENS_DAY, period.endDate());
    }

    @Test
    void 당일치기_기준일이_공휴일이면_그날이다() {
        TripPeriod period =
                PeriodStyle.DAY_TRIP.resolveFrom(CHILDRENS_DAY, PeriodOptions.none(), Set.of(CHILDRENS_DAY));

        assertEquals(CHILDRENS_DAY, period.startDate());
    }

    // ── 주말 포함 ────────────────────────────────────────────────

    private static List<Case> fridayBridgeCases() {
        return List.of(
                new Case(date(4), date(8), date(10), "월요일 기준 → 금·토·일"),
                new Case(date(8), date(8), date(10), "금요일 기준 → 그 주 금·토·일 (같은 날 포함)"),
                new Case(date(9), date(15), date(17), "토요일 기준 → 다음 주 금·토·일 (이번 금요일은 지났다)"));
    }

    @ParameterizedTest
    @MethodSource("fridayBridgeCases")
    void 주말포함_금요일_브릿지는_금토일_2박3일이다(Case testCase) {
        TripPeriod period = PeriodStyle.WEEKEND.resolveFrom(
                testCase.baseDate(), new PeriodOptions(WeekendBridge.FRIDAY, null), NO_HOLIDAY);

        assertEquals(testCase.expectedStart(), period.startDate(), testCase.what());
        assertEquals(testCase.expectedEnd(), period.endDate(), testCase.what());
        assertEquals(3, period.days(), "주말 포함은 2박 3일이다");
    }

    private static List<Case> mondayBridgeCases() {
        return List.of(
                new Case(date(4), date(9), date(11), "월요일 기준 → 토·일·월"),
                new Case(date(9), date(9), date(11), "토요일 기준 → 그 주 토·일·월 (같은 날 포함)"),
                new Case(date(10), date(16), date(18), "일요일 기준 → 다음 주 토·일·월"));
    }

    @ParameterizedTest
    @MethodSource("mondayBridgeCases")
    void 주말포함_월요일_브릿지는_토일월_2박3일이다(Case testCase) {
        TripPeriod period = PeriodStyle.WEEKEND.resolveFrom(
                testCase.baseDate(), new PeriodOptions(WeekendBridge.MONDAY, null), NO_HOLIDAY);

        assertEquals(testCase.expectedStart(), period.startDate(), testCase.what());
        assertEquals(testCase.expectedEnd(), period.endDate(), testCase.what());
        assertEquals(3, period.days(), "주말 포함은 2박 3일이다");
    }

    @Test
    void 주말포함은_브릿지_요일이_없으면_계약_예외다() {
        PeriodOptions noBridge = new PeriodOptions(null, 3);

        LeaveException ex = assertThrows(
                LeaveException.class, () -> PeriodStyle.WEEKEND.resolveFrom(date(4), noBridge, NO_HOLIDAY));
        assertEquals(LeaveErrorCode.WEEKEND_BRIDGE_REQUIRED.code(), ex.errorCode().code());
    }

    // ── 연차만 이어서 ──────────────────────────────────────────────

    private static List<Case> connectedThreeDayCases() {
        return List.of(
                new Case(date(4), date(4), date(6), "월요일 기준 → 월·화·수"),
                new Case(date(6), date(6), date(8), "수요일 기준 → 수·목·금 (금요일까지 딱 맞다)"),
                new Case(date(7), date(11), date(13), "목요일 기준 → 목·금·토가 주말에 걸려 다음 월·화·수"),
                new Case(date(9), date(11), date(13), "토요일 기준 → 다음 월·화·수"));
    }

    @ParameterizedTest
    @MethodSource("connectedThreeDayCases")
    void 연차만_3일은_주말이_끼지_않는_가장_이른_연속_평일이다(Case testCase) {
        TripPeriod period =
                PeriodStyle.CONNECTED.resolveFrom(testCase.baseDate(), new PeriodOptions(null, 3), NO_HOLIDAY);

        assertEquals(testCase.expectedStart(), period.startDate(), testCase.what());
        assertEquals(testCase.expectedEnd(), period.endDate(), testCase.what());
        assertEquals(3, period.days());
    }

    private static List<Case> connectedTwoDayCases() {
        return List.of(
                new Case(date(4), date(4), date(5), "월요일 기준 → 월·화"),
                new Case(date(7), date(7), date(8), "목요일 기준 → 목·금 (2일은 금요일까지 들어간다)"),
                new Case(date(8), date(11), date(12), "금요일 기준 → 금·토가 걸려 다음 월·화"));
    }

    @ParameterizedTest
    @MethodSource("connectedTwoDayCases")
    void 연차만_2일도_같은_규칙이다(Case testCase) {
        TripPeriod period =
                PeriodStyle.CONNECTED.resolveFrom(testCase.baseDate(), new PeriodOptions(null, 2), NO_HOLIDAY);

        assertEquals(testCase.expectedStart(), period.startDate(), testCase.what());
        assertEquals(testCase.expectedEnd(), period.endDate(), testCase.what());
        assertEquals(2, period.days());
    }

    @Test
    void 연차만은_공휴일이_끼어도_구간을_피하지_않는다() {
        // 05-04(월) 기준 3일 — 05-05(화)가 공휴일이지만 월·화·수 그대로다. 같은 일수에 연차만 덜 빠지므로 이득이다.
        TripPeriod period =
                PeriodStyle.CONNECTED.resolveFrom(date(4), new PeriodOptions(null, 3), Set.of(CHILDRENS_DAY));

        assertEquals(date(4), period.startDate());
        assertEquals(date(6), period.endDate());
    }

    @Test
    void 연차만은_연차_일수가_없으면_계약_예외다() {
        PeriodOptions noDays = new PeriodOptions(WeekendBridge.FRIDAY, null);

        LeaveException ex = assertThrows(
                LeaveException.class, () -> PeriodStyle.CONNECTED.resolveFrom(date(4), noDays, NO_HOLIDAY));
        assertEquals(LeaveErrorCode.LEAVE_DAYS_REQUIRED.code(), ex.errorCode().code());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 4, 10})
    void 연차만은_연차_일수가_2에서_3_밖이면_계약_예외다(int leaveDays) {
        PeriodOptions outOfRange = new PeriodOptions(null, leaveDays);

        LeaveException ex = assertThrows(
                LeaveException.class, () -> PeriodStyle.CONNECTED.resolveFrom(date(4), outOfRange, NO_HOLIDAY));
        assertEquals(LeaveErrorCode.INVALID_CONNECTED_LEAVE_DAYS.code(), ex.errorCode().code());
    }

    // ── 불변식 ──────────────────────────────────────────────────

    @Test
    void 기준일이나_보조파라미터가_null_이면_불변식_위반이다() {
        assertThrows(
                NullPointerException.class,
                () -> PeriodStyle.DAY_TRIP.resolveFrom(null, PeriodOptions.none(), NO_HOLIDAY));
        assertThrows(
                NullPointerException.class, () -> PeriodStyle.DAY_TRIP.resolveFrom(date(4), null, NO_HOLIDAY));
        assertThrows(
                NullPointerException.class,
                () -> PeriodStyle.DAY_TRIP.resolveFrom(date(4), PeriodOptions.none(), null));
    }

    @Test
    void 해석된_구간은_모두_여행_상한_안이다() {
        for (LocalDate baseDate = date(1); !baseDate.isAfter(date(31)); baseDate = baseDate.plusDays(1)) {
            assertMaxTripDays(PeriodStyle.DAY_TRIP.resolveFrom(baseDate, PeriodOptions.none(), NO_HOLIDAY));
            assertMaxTripDays(PeriodStyle.WEEKEND.resolveFrom(
                    baseDate, new PeriodOptions(WeekendBridge.FRIDAY, null), NO_HOLIDAY));
            assertMaxTripDays(
                    PeriodStyle.CONNECTED.resolveFrom(baseDate, new PeriodOptions(null, 3), NO_HOLIDAY));
        }
    }

    private static void assertMaxTripDays(TripPeriod period) {
        if (period.days() > AvailableTime.MAX_TRIP_DAYS) {
            throw new AssertionError("여행 상한을 넘는 구간이 해석됐다: " + period);
        }
    }
}
