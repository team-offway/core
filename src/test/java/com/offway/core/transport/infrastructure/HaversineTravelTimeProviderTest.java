package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.transport.service.TravelTimeProvider;
import org.junit.jupiter.api.Test;

class HaversineTravelTimeProviderTest {

    private final TravelTimeProvider provider = new HaversineTravelTimeProvider();

    private static final Coordinate SEOUL = new Coordinate(37.5665, 126.9780);
    private static final Coordinate BUSAN = new Coordinate(35.1796, 129.0756);

    @Test
    void 같은_좌표면_도달시간이_0이다() {
        assertEquals(0, provider.reachMinutes(SEOUL, SEOUL, TransportMode.CAR));
    }

    @Test
    void 대중교통이_자차보다_오래_걸린다() {
        int car = provider.reachMinutes(SEOUL, BUSAN, TransportMode.CAR);
        int transit = provider.reachMinutes(SEOUL, BUSAN, TransportMode.TRANSIT);
        assertTrue(transit > car, "transit=" + transit + " car=" + car);
    }

    @Test
    void 서울_부산_자차_도달시간이_현실_범위다() {
        // 직선 약 325㎞ × 우회 1.25 ≈ 406㎞ / 75㎞h ≈ 325분. 밴드로 잠근다(정확 캘리브레이션은 TMAP).
        int minutes = provider.reachMinutes(SEOUL, BUSAN, TransportMode.CAR);
        assertTrue(minutes >= 280 && minutes <= 360, "서울-부산 자차 도달시간(분)=" + minutes);
    }
}
