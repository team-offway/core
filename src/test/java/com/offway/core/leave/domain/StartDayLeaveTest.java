package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 첫날에 쓴 연차가 소모량과 출발 시각을 함께 정하는가(#138).
 *
 * <p>두 값이 <b>한 상수에 묶여 있는지</b>가 이 테스트의 요점이다. 따로 두면 "반차인데 08시 출발" 같은 어긋난
 * 조합을 누군가 만들고, 그때 코스는 지킬 수 없는 일정이 된다.
 */
class StartDayLeaveTest {

    @ParameterizedTest
    @CsvSource({
        "FULL_DAY, 1.0, 08:00",
        "HALF_DAY, 0.5, 12:00",
        "QUARTER_DAY, 0.25, 15:00"
    })
    void 단위마다_소모량과_출발_시각이_정해져_있다(StartDayLeave unit, double consumed, LocalTime departure) {
        assertEquals(consumed, unit.consumedLeave(), 0.001);
        assertEquals(departure, unit.departureTime());
    }

    @ParameterizedTest
    @EnumSource(StartDayLeave.class)
    void 모든_단위가_소모량과_시각을_갖는다(StartDayLeave unit) {
        // 상수를 새로 더할 때 값을 빠뜨리면 여기서 걸린다 — 0 소모나 자정 출발은 어느 단위에도 없다.
        assertTrue(unit.consumedLeave() > 0, "소모량이 0 이다: " + unit);
        assertNotEquals(LocalTime.MIDNIGHT, unit.departureTime(), "출발 시각이 비었다: " + unit);
        assertTrue(unit.label() != null && !unit.label().isBlank(), "표기가 비었다: " + unit);
    }

    @ParameterizedTest
    @EnumSource(StartDayLeave.class)
    void 덜_쓴_연차는_더_늦게_떠난다(StartDayLeave unit) {
        // 소모량과 출발 시각의 방향이 어긋나면(적게 쓰는데 일찍 떠남) 규칙 자체가 앞뒤가 안 맞는다.
        for (StartDayLeave other : StartDayLeave.values()) {
            if (unit.consumedLeave() < other.consumedLeave()) {
                assertTrue(
                        unit.departureTime().isAfter(other.departureTime()),
                        "%s(%s)가 %s(%s)보다 늦게 떠나야 한다".formatted(unit, unit.consumedLeave(), other, other.consumedLeave()));
            }
        }
    }

    @Test
    void 예전_불리언_계약을_옮겨온다() {
        // 앱이 갈아타는 동안 둘을 함께 받는다. true 는 반차, 그 밖(false·null)은 종일이다.
        assertEquals(StartDayLeave.HALF_DAY, StartDayLeave.fromHalfDayFlag(true));
        assertEquals(StartDayLeave.FULL_DAY, StartDayLeave.fromHalfDayFlag(false));
        assertEquals(StartDayLeave.FULL_DAY, StartDayLeave.fromHalfDayFlag(null));
    }

    @Test
    void 기본값은_종일이다() {
        assertEquals(StartDayLeave.FULL_DAY, StartDayLeave.DEFAULT);
        assertTrue(StartDayLeave.FULL_DAY.isFullDay());
        assertTrue(!StartDayLeave.HALF_DAY.isFullDay() && !StartDayLeave.QUARTER_DAY.isFullDay());
    }

    /**
     * 늦게 떠날수록 첫날 도달 상한이 좁아진다(#289).
     *
     * <p>값 자체보다 <b>순서</b>가 계약이다. 뒤집히면 반반차가 반차보다 멀리 갈 수 있게 되는데,
     * 화면은 멀쩡해 보여 아무도 모른다.
     */
    @Test
    void 늦게_떠날수록_첫날_도달_상한이_좁다() {
        assertTrue(
                StartDayLeave.FULL_DAY.firstDayReachMinutes()
                        > StartDayLeave.HALF_DAY.firstDayReachMinutes(),
                "종일이 반차보다 넓어야 한다");
        assertTrue(
                StartDayLeave.HALF_DAY.firstDayReachMinutes()
                        > StartDayLeave.QUARTER_DAY.firstDayReachMinutes(),
                "반차가 반반차보다 넓어야 한다");
    }

    /**
     * 종일은 상한을 두지 않는다 — 하루가 통째로 있어 여행일수 축이 답을 낸다.
     *
     * <p>여기가 유한한 값이 되면 종일 연차의 도달 한계가 조용히 깎인다.
     */
    @Test
    void 종일은_첫날_상한을_두지_않는다() {
        assertEquals(Integer.MAX_VALUE, StartDayLeave.FULL_DAY.firstDayReachMinutes());
    }

    @Test
    void 반차와_반반차의_첫날_상한을_고정한다() {
        // 12시 출발 + 2시간 30분 = 14시 30분 도착, 15시 출발 + 2시간 = 17시 도착.
        assertEquals(150, StartDayLeave.HALF_DAY.firstDayReachMinutes());
        assertEquals(120, StartDayLeave.QUARTER_DAY.firstDayReachMinutes());
    }
}
