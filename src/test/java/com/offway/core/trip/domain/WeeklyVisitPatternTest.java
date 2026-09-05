package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 요일별 방문 패턴(#394 · #395).
 *
 * <p>여기서 잠그는 것은 <b>"언제 값을 내지 않는가"</b> 다. 표본이 모자라거나 격차가 미미할 때 그럴듯한
 * 요일을 골라 내리면, 사용자가 그걸 보고 연차 날짜를 옮긴다.
 */
class WeeklyVisitPatternTest {

    /** 표본을 채우는 데 필요한 주 수보다 넉넉히 — 요일당 40일이 하한이다. */
    private static final int ENOUGH_WEEKS = 42;

    /** 2025-01-06 은 월요일 — 주 단위로 채울 때 요일이 고르게 들어가도록 월요일에서 시작한다. */
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2025, 1, 6);

    /** 모든 요일이 같은 값인 기준 패턴. 테스트마다 특정 요일만 덮어 쓴다. */
    private static Map<DayOfWeek, Double> flat(double value) {
        Map<DayOfWeek, Double> perDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            perDay.put(day, value);
        }
        return perDay;
    }

    private static Map<LocalDate, Double> weeks(int count, Map<DayOfWeek, Double> perDay) {
        Map<LocalDate, Double> byDate = new HashMap<>();
        for (int week = 0; week < count; week++) {
            for (DayOfWeek day : DayOfWeek.values()) {
                LocalDate date = FIRST_MONDAY.plusWeeks(week).plusDays(day.getValue() - 1L);
                byDate.put(date, perDay.get(day));
            }
        }
        return byDate;
    }

    @Test
    void 요일당_표본이_모자라면_패턴을_내지_않는다() {
        // 39주면 요일당 39일 — 하한 40일에 하나 모자란다.
        Optional<WeeklyVisitPattern> pattern = WeeklyVisitPattern.of(weeks(39, flat(100)));

        assertTrue(pattern.isEmpty(), "석 달치로 낸 요일계수는 사실 계절계수다");
    }

    @Test
    void 관측은_있는데_방문자가_전부_0이면_내지_않는다() {
        Optional<WeeklyVisitPattern> pattern = WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, flat(0)));

        assertTrue(pattern.isEmpty(), "분모가 0이면 요일을 가릴 근거가 없다");
    }

    @Test
    void 아무_값도_없으면_내지_않는다() {
        assertTrue(WeeklyVisitPattern.of(Map.of()).isEmpty());
    }

    /**
     * <b>이 테스트가 시안의 문구를 잠근다.</b> "화요일 방문객이 다른 요일보다 약 30% 적어요".
     *
     * <p>격차를 <b>나머지 요일 평균</b>과 견주는지도 함께 본다. 전체 평균(자기 자신이 섞인 값)과
     * 견주면 같은 데이터가 26% 로 나와 문구와 어긋난다.
     */
    @Test
    void 가장_한산한_요일과_나머지_대비_격차를_낸다() {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.TUESDAY, 70.0);

        QuietestDay quietest = WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay))
                .orElseThrow()
                .quietest()
                .orElseThrow();

        assertEquals(DayOfWeek.TUESDAY, quietest.dayOfWeek());
        assertEquals(30, quietest.percentLessThanOtherDays());
    }

    @Test
    void 요일_격차가_미미하면_한산한_날을_고르지_않는다() {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.TUESDAY, 91.0); // 9% — 하한 10% 미만

        Optional<QuietestDay> quietest =
                WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay)).orElseThrow().quietest();

        assertTrue(quietest.isEmpty(), "체감 못 할 차이로 연차를 옮기라 하면 조언이 아니라 소음이다");
    }

    @ParameterizedTest
    @CsvSource({
        "91, false", // 9% — 하한 미만
        "90, true", // 10% — 하한
        "70, true", // 30% — 시안 예시
    })
    void 격차_하한을_경계에서_가른다(double tuesdayValue, boolean shown) {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.TUESDAY, tuesdayValue);

        Optional<QuietestDay> quietest =
                WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay)).orElseThrow().quietest();

        assertEquals(shown, quietest.isPresent());
    }

    /**
     * <b>반올림한 값으로 문턱을 넘지 못한다.</b>
     *
     * <p>격차 9.5% 는 표시할 땐 10 이지만 실제로는 하한에 못 미친다. 반올림한 숫자로 자격을 판정하면
     * "화요일에 가장 한산해요" 가 뜨고, 사용자는 체감도 안 되는 차이 때문에 연차 날짜를 옮긴다.
     */
    @ParameterizedTest
    @CsvSource({
        "90.5, false", // 잰 값 9.5% — 반올림하면 10 이지만 하한 미달
        "90.1, false", // 9.9%
        "90.0, true", // 정확히 10%
    })
    void 표시값이_10이어도_잰_격차가_모자라면_고르지_않는다(double tuesdayValue, boolean shown) {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.TUESDAY, tuesdayValue);

        Optional<QuietestDay> quietest =
                WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay)).orElseThrow().quietest();

        assertEquals(shown, quietest.isPresent());
    }

    @Test
    void 모든_요일이_같으면_한산한_날이_없다() {
        Optional<QuietestDay> quietest =
                WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, flat(100))).orElseThrow().quietest();

        assertTrue(quietest.isEmpty());
    }

    /**
     * 요일계수는 <b>그 지역 자신의 평균 대비</b>다(#395). 절대값이 아니라 비율이라, 울릉군과 안동시를
     * 같은 자로 잰다.
     */
    @Test
    void 요일계수는_그_지역_평균_대비다() {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.SATURDAY, 200.0);

        WeeklyVisitPattern pattern = WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay)).orElseThrow();

        // 전체 평균 = (100×6 + 200) / 7 = 114.29
        assertEquals(200 / (800 / 7.0), pattern.factorOf(DayOfWeek.SATURDAY), 0.001);
        assertEquals(100 / (800 / 7.0), pattern.factorOf(DayOfWeek.MONDAY), 0.001);
    }

    @Test
    void 붐비는_요일의_계수는_1보다_크다() {
        Map<DayOfWeek, Double> perDay = flat(100);
        perDay.put(DayOfWeek.SATURDAY, 140.0);

        WeeklyVisitPattern pattern = WeeklyVisitPattern.of(weeks(ENOUGH_WEEKS, perDay)).orElseThrow();

        assertTrue(pattern.factorOf(DayOfWeek.SATURDAY) > 1.0);
        assertFalse(pattern.factorOf(DayOfWeek.MONDAY) > 1.0);
    }
}
