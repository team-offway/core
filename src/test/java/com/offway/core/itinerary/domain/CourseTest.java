package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static Slot slot(int order) {
        return Slot.of(order, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + order, "장소" + order, 37.5, 127.0,
                order == 1 ? 0 : 10);
    }

    private static DaySchedule day(int dayNumber, int slots) {
        return DaySchedule.of(dayNumber, java.util.stream.IntStream.rangeClosed(1, slots)
                .mapToObj(CourseTest::slot).toList());
    }

    @Test
    void 유효한_코스는_기간을_받아_들고_전체슬롯을_센다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2)), null, 2);

        assertEquals(42L, course.getRegionId());
        assertEquals(2, course.getTravelDays());
        assertEquals(Density.PACKED, course.getDensity());
        assertEquals(5, course.totalSlots()); // 3 + 2
    }

    @Test
    void 하루도_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of(), null, 1));
    }

    @Test
    void 최대_2박3일을_초과하면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR,
                        List.of(day(1, 1), day(2, 1), day(3, 1), day(4, 1)), null, 3));
    }

    @Test
    void 일차가_1부터_연속이_아니면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of(day(1, 1), day(3, 1)), null, 2));
    }

    @Test
    void 일정이_없는_날이_있어도_기간은_요청한_일수다() {
        // 첫날이 이동뿐이면 그 날은 코스에서 빠진다(#159). 그래도 여행은 3일짜리다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.TRANSIT,
                List.of(DaySchedule.of(1, 1, List.of(slot(1))), DaySchedule.of(2, 2, List.of(slot(1)))),
                LocalDate.of(2026, 9, 11), 3);

        assertEquals(3, course.getTravelDays(), "표시 일수(2)가 아니라 달력 기간(3)");
        assertEquals(LocalDate.of(2026, 9, 13), course.travelEndDate(),
                "종료일이 하루 이르면 연차가 덜 차감되고 \"다녀오셨나요\" 가 일찍 뜬다(#164)");
        assertFalse(course.hasEndedBy(LocalDate.of(2026, 9, 13)), "종료 당일은 아직 여행 중이다");
        assertTrue(course.hasEndedBy(LocalDate.of(2026, 9, 14)));
    }

    @Test
    void 기간_밖에_일정이_있으면_거부한다() {
        // 기간을 잘못 넘기면 여기서 걸려야 한다 — 조용히 통과하면 연차 차감이 그만큼 어긋난다.
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR,
                        List.of(DaySchedule.of(1, 0, List.of(slot(1))), DaySchedule.of(2, 2, List.of(slot(1)))),
                        LocalDate.of(2026, 9, 11), 2));
    }

    @Test
    void 기간이_범위를_벗어나면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 1)), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 1)), null, 4));
    }
}
