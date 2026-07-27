package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.service.RouteTimeProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmapRouteTimeProviderTest {

    private static final Coordinate FROM = new Coordinate(37.5665, 126.9780);
    private static final Coordinate TO = new Coordinate(35.1796, 129.0756);

    @Test
    void TMAP_실측이_있으면_그_시간을_쓴다() {
        TmapClient stub = (from, to) -> Optional.of(new TmapRoute(42, 30.0));
        RouteTimeProvider provider = new TmapRouteTimeProvider(stub, new HaversineTravelTimeProvider());

        assertEquals(42, provider.drivingMinutes(FROM, TO));
    }

    @Test
    void TMAP가_없으면_직선거리_근사로_폴백한다() {
        TmapClient empty = (from, to) -> Optional.empty();
        RouteTimeProvider provider = new TmapRouteTimeProvider(empty, new HaversineTravelTimeProvider());

        int minutes = provider.drivingMinutes(FROM, TO);

        assertTrue(minutes > 0); // Haversine 폴백값(서울~부산은 수 시간대)
    }
}
