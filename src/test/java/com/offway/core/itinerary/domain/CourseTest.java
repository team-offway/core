package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.transport.domain.TransportMode;
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
    void 유효한_코스는_일수를_일정수에서_도출하고_전체슬롯을_센다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2)));

        assertEquals(42L, course.getRegionId());
        assertEquals(2, course.getTravelDays()); // days.size 에서 도출
        assertEquals(Density.PACKED, course.getDensity());
        assertEquals(5, course.totalSlots()); // 3 + 2
    }

    @Test
    void 하루도_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of()));
    }

    @Test
    void 최대_2박3일을_초과하면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR,
                        List.of(day(1, 1), day(2, 1), day(3, 1), day(4, 1))));
    }

    @Test
    void 일차가_1부터_연속이_아니면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of(day(1, 1), day(3, 1))));
    }
}
