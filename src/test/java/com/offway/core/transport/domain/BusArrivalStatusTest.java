package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BusArrivalStatusTest {

    @Test
    void 도착_예정_버스를_빠른_순으로_정렬한다() {
        // 외부가 어떤 순서로 주든 "다음 버스"가 첫 항목이어야 한다.
        BusArrivalStatus.Arriving arriving = new BusArrivalStatus.Arriving(List.of(
                new BusArrival("3", "일반버스", 900, 7),
                new BusArrival("1", "농어촌버스", 180, 2),
                new BusArrival("2", "일반버스", 420, 4)));

        assertEquals(List.of("1", "2", "3"), arriving.arrivals().stream().map(BusArrival::routeNo).toList());
        assertEquals("1", arriving.soonest().routeNo());
    }

    @Test
    void 빈_목록으로는_Arriving을_만들_수_없다() {
        // 비었다는 사실은 NoBusSoon 이 표현한다. 둘을 뭉뚱그리면 호출부가 빈 목록을 또 검사해야 한다.
        assertThrows(IllegalArgumentException.class, () -> new BusArrivalStatus.Arriving(List.of()));
    }
}
