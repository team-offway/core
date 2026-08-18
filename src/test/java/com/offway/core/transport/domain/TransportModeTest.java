package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransportModeTest {

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

    /**
     * 이 enum 은 <b>거리 → 시간</b> 하나만 안다(#289).
     *
     * <p>예전에는 도달 한계 분(分)까지 0.7 로 깎는 {@code applyReach} 가 있었는데, 같은 감쇠를 평균속도와
     * 두 번 걸어 대중교통 추천이 서울 기준 3곳까지 줄었다. 분 예산은 여행이 정하는 값이라 여기서 손대지 않는다.
     */
    @Test
    void 비유한_또는_음수_거리는_불변식_위반이다() {
        assertThrows(IllegalArgumentException.class, () -> TransportMode.CAR.travelMinutes(-1));
        assertThrows(IllegalArgumentException.class, () -> TransportMode.CAR.travelMinutes(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> TransportMode.TRANSIT.travelMinutes(Double.POSITIVE_INFINITY));
    }
}
