package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.service.RouteTimeProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmapRouteTimeProviderTest {

    private static final Coordinate FROM = new Coordinate(37.5665, 126.9780);
    private static final Coordinate TO = new Coordinate(35.1796, 129.0756);

    /** carRoute 만 정해 주는 stub — optimizeCarOrder 는 이 테스트와 무관해 빈 값. */
    private static TmapClient carRouteStub(Optional<TmapRoute> route) {
        return new TmapClient() {
            @Override
            public Optional<TmapRoute> carRoute(Coordinate origin, Coordinate destination) {
                return route;
            }

            @Override
            public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
                return Optional.empty();
            }
        };
    }

    @Test
    void TMAP_실측이_있으면_그_시간을_쓴다() {
        RouteTimeProvider provider =
                new TmapRouteTimeProvider(carRouteStub(Optional.of(new TmapRoute(42, 30.0))), new HaversineTravelTimeProvider());

        assertEquals(42, provider.drivingMinutes(FROM, TO));
    }

    @Test
    void TMAP가_없으면_직선거리_근사로_폴백한다() {
        RouteTimeProvider provider =
                new TmapRouteTimeProvider(carRouteStub(Optional.empty()), new HaversineTravelTimeProvider());

        int minutes = provider.drivingMinutes(FROM, TO);

        assertTrue(minutes > 0); // Haversine 폴백값(서울~부산은 수 시간대)
    }
}
