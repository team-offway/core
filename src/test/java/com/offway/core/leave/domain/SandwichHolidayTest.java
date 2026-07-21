package com.offway.core.leave.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SandwichHolidayTest {

    private static final Set<LocalDate> NO_HOLIDAY = Set.of();

    /** 요일 하드코딩을 피하려 월요일을 앵커로 고정한다. MON=+0 … THU=+3 · FRI=+4 · SAT=+5 · SUN=+6. */
    private static final LocalDate MON =
            LocalDate.of(2026, 5, 4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    // ---------- 탐지 (detectWithin) ----------

    @Test
    void 목요일_공휴일이면_금요일_하나로_금토일까지_잇는다() {
        LocalDate thursdayHoliday = MON.plusDays(3);

        List<SandwichHoliday> found =
                SandwichHoliday.detectWithin(MON, MON.plusDays(6), Set.of(thursdayHoliday));

        assertEquals(1, found.size());
        SandwichHoliday sandwich = found.get(0);
        assertThat(sandwich.leaveDates()).containsExactly(MON.plusDays(4)); // 금요일 한 장
        assertEquals(4, sandwich.totalRestDays()); // 목·금·토·일
        assertEquals(MON.plusDays(3), sandwich.windowStart());
        assertEquals(MON.plusDays(6), sandwich.windowEnd());
    }

    @Test
    void 주말과_공휴일_사이_평일_이틀을_징검다리로_잇는다() {
        // 토·일(주말) → 월·화(연차) → 수(공휴일) = 5일 휴식
        LocalDate saturday = MON.minusDays(2);
        LocalDate wednesdayHoliday = MON.plusDays(2);

        List<SandwichHoliday> found =
                SandwichHoliday.detectWithin(saturday, wednesdayHoliday, Set.of(wednesdayHoliday));

        assertEquals(1, found.size());
        SandwichHoliday sandwich = found.get(0);
        assertThat(sandwich.leaveDates()).containsExactly(MON, MON.plusDays(1)); // 월·화
        assertEquals(5, sandwich.totalRestDays());
        assertEquals(2, sandwich.leaveDays());
    }

    @Test
    void 세_평일_이상_떨어진_휴식은_잇지_않는다() {
        // 월(공휴일) … 화·수·목(평일 3일) … 금(공휴일) → 3일은 다리로 안 잇는다
        List<SandwichHoliday> found =
                SandwichHoliday.detectWithin(MON, MON.plusDays(4), Set.of(MON, MON.plusDays(4)));

        assertThat(found).isEmpty();
    }

    @Test
    void 공휴일이_없는_평범한_기간은_추천이_없다() {
        List<SandwichHoliday> found = SandwichHoliday.detectWithin(MON, MON.plusDays(20), NO_HOLIDAY);

        assertThat(found).isEmpty();
    }

    @Test
    void 효율이_높은_순으로_정렬한다() {
        // 목요일 공휴일 클러스터(연차1=휴식4, eff 4.0) + 수요일 공휴일 클러스터(연차4=휴식9, eff 2.25)
        LocalDate wednesdayHoliday = MON.plusDays(2);
        LocalDate thursdayHoliday = MON.plusDays(17);

        List<SandwichHoliday> found = SandwichHoliday.detectWithin(
                MON.minusDays(2), MON.plusDays(22), Set.of(wednesdayHoliday, thursdayHoliday));

        assertEquals(2, found.size());
        assertEquals(4.0, found.get(0).efficiency(), 0.001); // 효율 높은 게 먼저
        assertEquals(1, found.get(0).leaveDays());
        assertEquals(2.25, found.get(1).efficiency(), 0.001);
        assertEquals(4, found.get(1).leaveDays());
    }

    @Test
    void 실제_2026년_5월_예시_노동절과_어린이날을_잇는다() {
        // 5/1(금·노동절) + 5/2~3(주말) + 5/4(연차) + 5/5(화·어린이날) = 5일 휴식
        LocalDate laborDay = LocalDate.of(2026, 5, 1);
        LocalDate childrensDay = LocalDate.of(2026, 5, 5);

        List<SandwichHoliday> found = SandwichHoliday.detectWithin(
                laborDay, LocalDate.of(2026, 5, 7), Set.of(laborDay, childrensDay));

        assertEquals(1, found.size());
        SandwichHoliday sandwich = found.get(0);
        assertThat(sandwich.leaveDates()).containsExactly(LocalDate.of(2026, 5, 4)); // 문서 오타(5/2) 아님
        assertEquals(5, sandwich.totalRestDays());
        assertEquals(5.0, sandwich.efficiency(), 0.001);
        assertTrue(sandwich.isGolden());
    }

    // ---------- 판정 (isGolden / efficiency) ----------

    @Test
    void 연차1당_휴식2일이면_황금연차다() {
        // 월~목(4일) 중 월·화 연차 → eff 2.0 = 경계
        SandwichHoliday sandwich =
                new SandwichHoliday(MON, MON.plusDays(3), List.of(MON, MON.plusDays(1)));

        assertEquals(2.0, sandwich.efficiency(), 0.001);
        assertTrue(sandwich.isGolden());
    }

    @Test
    void 연차1당_휴식2일_미만이면_황금연차가_아니다() {
        // 월~수(3일) 중 월·화 연차 → eff 1.5
        SandwichHoliday sandwich =
                new SandwichHoliday(MON, MON.plusDays(2), List.of(MON, MON.plusDays(1)));

        assertEquals(1.5, sandwich.efficiency(), 0.001);
        assertFalse(sandwich.isGolden());
    }

    // ---------- 불변식 ----------

    @Test
    void 시작일이_종료일보다_늦으면_불변식_위반이다() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandwichHoliday(MON.plusDays(2), MON, List.of(MON.plusDays(1))));
    }

    @Test
    void 연차일이_하루도_없으면_샌드위치가_아니다() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandwichHoliday(MON, MON.plusDays(2), List.of()));
    }

    @Test
    void 연차일이_구간_밖이면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandwichHoliday(MON, MON.plusDays(2), List.of(MON.plusDays(5))));
    }

    @Test
    void 주말을_연차일로_넣으면_거부한다() {
        LocalDate saturday = MON.plusDays(5);

        assertThrows(IllegalArgumentException.class,
                () -> new SandwichHoliday(MON, MON.plusDays(6), List.of(saturday)));
    }

    @Test
    void 연차일에_중복이_있으면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandwichHoliday(MON, MON.plusDays(2), List.of(MON, MON)));
    }

    @Test
    void 연차일이_null이면_거부한다() {
        assertThrows(NullPointerException.class,
                () -> new SandwichHoliday(MON, MON.plusDays(2), null));
    }

    @Test
    void 탐지_조회종료일이_시작일보다_앞서면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> SandwichHoliday.detectWithin(MON.plusDays(2), MON, NO_HOLIDAY));
    }

    @Test
    void 탐지_공휴일_집합이_null이면_거부한다() {
        assertThrows(NullPointerException.class,
                () -> SandwichHoliday.detectWithin(MON, MON.plusDays(2), null));
    }
}
