package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BusStopAccessTest {

    private static final BusStop TERMINAL = new BusStop("GMB165", "정선터미널", 32020, 37.3801, 128.6604);
    private static final BusStop OFFICE = new BusStop("GMB166", "정선읍사무소", 32020, 37.3812, 128.6631);

    @Test
    void 가장_가까운_정류소는_응답_순서의_첫_항목이다() {
        // TAGO 근접조회가 이미 가까운 순으로 준다 — 다시 정렬하면 외부가 준 거리 판단을 덮어쓴다.
        BusStopAccess.Available available = new BusStopAccess.Available(List.of(TERMINAL, OFFICE));

        assertEquals("정선터미널", available.nearest().name());
    }

    @Test
    void 빈_목록으로는_Available을_만들_수_없다() {
        // 비었다는 사실은 NoStopNearby 가 표현한다.
        assertThrows(IllegalArgumentException.class, () -> new BusStopAccess.Available(List.of()));
    }
}
