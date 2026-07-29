package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BusArrivalStatusTest {

    @Test
    void 도착_예정_버스를_빠른_순으로_정렬한다() {
        // 외부가 어떤 순서로 주든 "다음 버스"가 첫 항목이어야 한다.
        // routeNo(1·2·3)와 도착 순서를 일부러 어긋나게 둬, routeNo 로 잘못 정렬하면 실패하게 한다.
        BusArrivalStatus.Arriving arriving = new BusArrivalStatus.Arriving(List.of(
                new BusArrival("1", "일반버스", 900, 7),
                new BusArrival("2", "농어촌버스", 180, 2),
                new BusArrival("3", "일반버스", 420, 4)));

        // 도착초(180<420<900) 기준이면 2·3·1, routeNo 기준이면 1·2·3 — 둘이 달라 정렬 기준을 검증한다.
        assertEquals(List.of("2", "3", "1"), arriving.arrivals().stream().map(BusArrival::routeNo).toList());
        assertEquals("2", arriving.soonest().routeNo());
    }

    @Test
    void 빈_목록으로는_Arriving을_만들_수_없다() {
        // 비었다는 사실은 NoBusSoon 이 표현한다. 둘을 뭉뚱그리면 호출부가 빈 목록을 또 검사해야 한다.
        assertThrows(IllegalArgumentException.class, () -> new BusArrivalStatus.Arriving(List.of()));
    }
}
