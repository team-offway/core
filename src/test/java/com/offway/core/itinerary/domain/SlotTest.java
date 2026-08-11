package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void 표시_정보를_값객체로_받아_순서를_섞을_수_없게_한다() {
        // 이미지·주소·캐치프레이즈·전화가 전부 String 이라, 인자로 줄세우면 주소 자리에 캐치프레이즈가
        // 들어가도 컴파일이 통과한다. 값객체로 묶어 이름이 붙게 했다.
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "완도타워", 34.3, 126.7, 0,
                new SlotDisplay("http://img/1.jpg", "전남 완도군", "바다 위에 뜬 낭만", "061-550-6000"));

        assertEquals("http://img/1.jpg", slot.getImageUrl());
        assertEquals("전남 완도군", slot.getAddress());
        assertEquals("바다 위에 뜬 낭만", slot.getCatchphrase());
        assertEquals("061-550-6000", slot.getTel());
    }

    @Test
    void 표시_정보가_없으면_전부_null_이다() {
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, "c1", "완도타워", 34.3, 126.7, 0);

        assertNull(slot.getTel());
        assertNull(slot.getImageUrl());
    }
}
