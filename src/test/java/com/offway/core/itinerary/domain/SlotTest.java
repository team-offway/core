package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlotTest {

    @Test
    void 유효한_슬롯을_만든다() {
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "126508", "가사동백숲해변", 34.36, 126.92, 0);

        assertEquals(1, slot.getOrderInDay());
        assertEquals(SlotKind.SIGHT, slot.getKind());
        assertEquals("126508", slot.getPoiContentId());
        assertEquals(0, slot.getTravelMinutesFromPrev());
    }

    @Test
    void 순서가_1미만이면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(0, TimeOfDay.MORNING, SlotKind.SIGHT, "c", "장소", 34.0, 126.0, 0));
    }

    @Test
    void 이동시간이_음수면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c", "장소", 34.0, 126.0, -1));
    }

    @Test
    void 하루_첫_슬롯의_이동시간이_0이_아니면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c", "장소", 34.0, 126.0, 10));
    }

    @ParameterizedTest
    @CsvSource({"91.0,126.0", "-91.0,126.0", "34.0,181.0", "34.0,-181.0"})
    void 좌표가_범위를_벗어나면_거부한다(double lat, double lng) {
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c", "장소", lat, lng, 0));
    }

    @Test
    void 빈_제목이나_POI면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "  ", "장소", 34.0, 126.0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c", "", 34.0, 126.0, 0));
    }
}
