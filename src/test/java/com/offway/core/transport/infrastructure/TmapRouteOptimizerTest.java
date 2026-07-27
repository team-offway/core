package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.service.RouteOptimizer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TmapRouteOptimizerTest {

    private static final List<Coordinate> FOUR = List.of(
            new Coordinate(37.0, 127.0), new Coordinate(37.5, 127.5),
            new Coordinate(37.1, 127.1), new Coordinate(37.9, 127.9));

    /** optimizeCarOrder 만 정해 주는 stub. */
    private static TmapClient orderStub(Optional<List<Integer>> order) {
        return new TmapClient() {
            @Override
            public Optional<TmapRoute> carRoute(Coordinate origin, Coordinate destination) {
                return Optional.empty();
            }

            @Override
            public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
                return order;
            }
        };
    }

    @Test
    void TMAP_최적순서가_있으면_그대로_쓴다() {
        RouteOptimizer optimizer = new TmapRouteOptimizer(orderStub(Optional.of(List.of(0, 2, 1, 3))));

        assertEquals(List.of(0, 2, 1, 3), optimizer.optimalOrder(FOUR));
    }

    @Test
    void TMAP가_없으면_최근접_폴백으로_전체를_한번씩_방문한다() {
        RouteOptimizer optimizer = new TmapRouteOptimizer(orderStub(Optional.empty()));

        List<Integer> order = optimizer.optimalOrder(FOUR);

        assertEquals(0, order.get(0)); // 첫 지점 고정
        assertEquals(new HashSet<>(List.of(0, 1, 2, 3)), new HashSet<>(order)); // 전부 한 번씩
    }

    @Test
    void 두곳_이하는_순서를_그대로_둔다() {
        RouteOptimizer optimizer = new TmapRouteOptimizer(orderStub(Optional.empty()));

        assertEquals(List.of(0, 1), optimizer.optimalOrder(FOUR.subList(0, 2)));
    }
}
