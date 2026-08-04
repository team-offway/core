package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 슬롯 사이 거리(#141) — 화면이 장소와 장소 사이에 {@code 8.3km}·{@code 154m} 를 그린다.
 *
 * <p>좌표가 이미 있어 <b>추가 외부 호출 없이</b> 구한다. 이동시간(TMAP 실측)과는 다른 값이다 — 그쪽은 도로를
 * 따라가고 이쪽은 직선이라, 둘을 섞어 쓰면 "8km 인데 40분" 같은 조합이 이상해 보일 수 있으나 실제로 그렇다.
 */
class SlotDistanceTest {

    private static Slot slot(int order, double lat, double lng) {
        return Slot.of(order, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + order, "장소" + order, lat, lng, 0);
    }

    @Test
    void 첫_슬롯은_거리가_없다() {
        // 이동 전이라 0 이 아니라 '없음' 이다. 0 으로 두면 화면이 "0m" 를 그린다.
        assertNull(DaySchedule.of(1, List.of(slot(1, 37.5, 127.0))).distanceFromPrevMeters(0));
    }

    @Test
    void 앞_슬롯과의_거리를_미터로_준다() {
        // 서울시청(37.5665,126.9780) → 광화문(37.5759,126.9769) ≈ 1.05km
        DaySchedule day = DaySchedule.of(
                1, List.of(slot(1, 37.5665, 126.9780), slot(2, 37.5759, 126.9769)));

        Integer meters = day.distanceFromPrevMeters(1);

        assertTrue(meters > 900 && meters < 1200, "약 1km 여야 한다: " + meters);
    }

    @Test
    void 좌표가_없는_슬롯은_거리를_구하지_않는다() {
        // 좌표는 필수라 여기 닿지 않는 게 정상이지만, 닿으면 지어내지 않고 비운다.
        DaySchedule day = DaySchedule.of(1, List.of(slot(1, 37.5, 127.0), slot(2, 37.5, 127.0)));

        assertEquals(0, day.distanceFromPrevMeters(1), "같은 좌표면 0m");
    }
}
