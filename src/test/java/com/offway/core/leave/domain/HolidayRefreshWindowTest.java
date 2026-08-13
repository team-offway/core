package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolidayRefreshWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);
    private static final YearMonth CURRENT = YearMonth.of(2026, 8);

    private static StoredHolidayMonth storedOn(YearMonth month, LocalDate updatedOn) {
        return StoredHolidayMonth.of(month, Set.of(), updatedOn.atTime(3, 0));
    }

    @Test
    void 지난달부터_열세달_뒤까지_열다섯_달을_덮는다() {
        List<YearMonth> months = HolidayRefreshWindow.of(TODAY).targetMonths();

        assertEquals(15, months.size());
        assertEquals(YearMonth.of(2026, 7), months.get(0));
        assertEquals(YearMonth.of(2027, 9), months.get(months.size() - 1));
    }

    @Test
    void 덮는_달은_오름차순이고_빠짐없이_이어진다() {
        List<YearMonth> months = HolidayRefreshWindow.of(TODAY).targetMonths();

        for (int i = 1; i < months.size(); i++) {
            assertEquals(months.get(i - 1).plusMonths(1), months.get(i));
        }
    }

    @Test
    void 샌드위치_조회_상한인_열두달_뒤가_덮는_범위_안에_있다() {
        // 이 범위가 좁으면 정상 요청이 요청 경로에서 외부를 물게 된다 — 범위의 존재 이유를 못 박는다.
        List<YearMonth> months = HolidayRefreshWindow.of(TODAY).targetMonths();

        assertTrue(months.contains(YearMonth.from(TODAY.plusMonths(12))));
    }

    @Test
    void 받은_적_없는_달은_받는다() {
        HolidayRefreshWindow window = HolidayRefreshWindow.of(TODAY);

        assertTrue(window.needsRefresh(YearMonth.of(2026, 9), null));
    }

    @Test
    void 지난달은_한_번_받으면_다시_묻지_않는다() {
        HolidayRefreshWindow window = HolidayRefreshWindow.of(TODAY);
        StoredHolidayMonth stored = storedOn(YearMonth.of(2026, 7), LocalDate.of(2026, 1, 1));

        assertFalse(window.needsRefresh(YearMonth.of(2026, 7), stored));
    }

    @ParameterizedTest(name = "{0} 에 받아둔 이번 달 → 다시 묻는가 {1}")
    @CsvSource({"2026-08-10, false", "2026-08-09, true"})
    void 이번_달은_오늘_받았으면_건너뛰고_아니면_다시_묻는다(LocalDate updatedOn, boolean expected) {
        // 같은 날 재배포가 외부를 다시 부르지 않게 하는 규칙이자, 미공표 달이 다음 날 반영되게 하는 규칙이다.
        HolidayRefreshWindow window = HolidayRefreshWindow.of(TODAY);

        assertEquals(expected, window.needsRefresh(CURRENT, storedOn(CURRENT, updatedOn)));
    }

    @Test
    void 미래_달도_이번_달과_같은_규칙을_따른다() {
        HolidayRefreshWindow window = HolidayRefreshWindow.of(TODAY);
        YearMonth future = YearMonth.of(2027, 3);

        assertFalse(window.needsRefresh(future, storedOn(future, TODAY)));
        assertTrue(window.needsRefresh(future, storedOn(future, TODAY.minusDays(1))));
    }

    @Test
    void 갱신_시각이_그날_늦은_시각이어도_오늘_받은_것으로_본다() {
        HolidayRefreshWindow window = HolidayRefreshWindow.of(TODAY);
        StoredHolidayMonth lateNight =
                StoredHolidayMonth.of(CURRENT, Set.of(), LocalDateTime.of(2026, 8, 10, 23, 59));

        assertFalse(window.needsRefresh(CURRENT, lateNight));
    }
}
