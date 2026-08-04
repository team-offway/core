package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 며칠째인지(표시 번호)와 달력상 며칠 뒤인지(오프셋)를 나눠 다루는지 확인한다(#159).
 *
 * <p>첫날에 일정이 하나도 안 잡히면 — 늦게 도착해 아무것도 못 하는 경우 — 그날은 코스에서 빠지고
 * 둘째 날이 {@code day 1} 이 된다. 그때 날짜를 표시 번호로 계산하면 **하루가 앞당겨진다.**
 * 표시는 1부터 이어지되 날짜는 실제 오프셋을 따라야 한다.
 */
class DayOffsetTest {

    private static final LocalDate TRAVEL_START = LocalDate.of(2026, 9, 11); // 금요일

    private static Slot slot(int order) {
        return Slot.of(order, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + order, "장소" + order, 37.5, 127.0, 0);
    }

    private static DaySchedule day(int dayNumber, int dayOffset) {
        return DaySchedule.of(dayNumber, dayOffset, List.of(slot(1)));
    }

    @Test
    void 오프셋이_없으면_표시_번호에서_하루씩_민다() {
        DaySchedule first = day(1, 0);

        assertEquals(0, first.getDayOffset());
    }

    /** 첫날이 통째로 빠진 코스 — 표시는 1·2 지만 실제로는 둘째·셋째 날이다. */
    @Test
    void 첫날이_빠지면_표시는_1부터_날짜는_이틀째부터다() {
        Course course = Course.of(76L, Density.RELAXED, TransportMode.TRANSIT,
                List.of(day(1, 1), day(2, 2)), TRAVEL_START);

        assertEquals(1, course.getDays().get(0).getDayNumber(), "표시 번호는 1부터");
        assertEquals(LocalDate.of(2026, 9, 12), course.dateOf(course.getDays().get(0)), "날짜는 이틀째");
        assertEquals(LocalDate.of(2026, 9, 13), course.dateOf(course.getDays().get(1)));
    }

    @Test
    void 첫날부터_일정이_있으면_표시와_날짜가_같이_간다() {
        Course course = Course.of(76L, Density.RELAXED, TransportMode.CAR,
                List.of(day(1, 0), day(2, 1)), TRAVEL_START);

        assertEquals(TRAVEL_START, course.dateOf(course.getDays().get(0)));
        assertEquals(TRAVEL_START.plusDays(1), course.dateOf(course.getDays().get(1)));
    }

    /** 날짜 없이 저장된 코스(#111 이전)는 날짜를 알 수 없다 — 지어내지 않는다. */
    @Test
    void 여행_날짜가_없으면_날짜도_없다() {
        Course course = Course.of(76L, Density.RELAXED, TransportMode.CAR, List.of(day(1, 0)), null);

        assertEquals(null, course.dateOf(course.getDays().get(0)));
    }

    @Test
    void 오프셋이_음수면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> DaySchedule.of(1, -1, List.of(slot(1))));
    }

    /** 표시 번호는 1부터 연속이어야 한다 — 오프셋이 떨어져 있어도 화면의 탭은 이어진다. */
    @Test
    void 표시_번호는_여전히_1부터_연속이어야_한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(76L, Density.RELAXED, TransportMode.CAR,
                        List.of(day(1, 0), day(3, 2)), TRAVEL_START));
    }
}
