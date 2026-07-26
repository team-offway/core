package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DayScheduleTest {

    private static Slot slot(int order) {
        return Slot.of(order, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + order, "장소" + order, 37.5, 127.0,
                order == 1 ? 0 : 10);
    }

    @Test
    void 유효한_하루일정을_만든다() {
        DaySchedule day = DaySchedule.of(1, List.of(slot(1), slot(2), slot(3)));

        assertEquals(1, day.getDayNumber());
        assertEquals(3, day.slotCount());
    }

    @Test
    void 일차가_1미만이면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> DaySchedule.of(0, List.of(slot(1))));
    }

    @Test
    void 슬롯이_비면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> DaySchedule.of(1, List.of()));
    }

    @Test
    void 슬롯_순서가_1부터_연속이_아니면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> DaySchedule.of(1, List.of(slot(1), slot(3))));
    }
}
