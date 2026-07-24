package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransportModeTest {

    @Test
    void 자차는_기준반경_그대로_대중교통은_0_7배로_적용한다() {
        assertEquals(240, TransportMode.CAR.applyReach(240));
        assertEquals(168, TransportMode.TRANSIT.applyReach(240)); // 240 × 0.7
    }

    @Test
    void 거리를_수단별_평균속도로_이동시간_분으로_환산한다() {
        assertEquals(60, TransportMode.CAR.travelMinutes(75.0)); // 75㎞ / 75㎞h
        assertEquals(120, TransportMode.CAR.travelMinutes(150.0));
        assertEquals(60, TransportMode.TRANSIT.travelMinutes(50.0)); // 50㎞ / 50㎞h
        assertEquals(180, TransportMode.TRANSIT.travelMinutes(150.0)); // 150 / 50 × 60
    }

    @Test
    void 같은_거리면_대중교통이_자차보다_오래_걸린다() {
        assertTrue(TransportMode.TRANSIT.travelMinutes(100) > TransportMode.CAR.travelMinutes(100));
    }
}
