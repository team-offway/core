package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.service.CarRouteCacheService;
import com.offway.core.transport.service.CarRouteCaches;
import com.offway.core.transport.service.RouteOptimizer;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.UnroutableCoordinateService;
import com.offway.core.transport.repository.UnroutableProbeRepository;
import com.offway.core.transport.domain.UnroutableProbe;
import com.offway.core.transport.domain.CoordinateKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * <b>같은 것을 두 번 물으면 TMAP 을 한 번만 부르는가</b>(#584).
 *
 * <p>이 작업의 판정이 이 한 문장이다. 캐시를 넣어 놓고 적중을 확인하지 않으면, 키가 어긋나 <b>조용히
 * 계속 부르고 있어도 아무도 모른다</b> — 그리고 그 증상이 정확히 캐시를 넣기 전과 같다.
 *
 * <p>특히 경유지 최적화는 <b>일일 한도가 50</b> 이라, 안 먹으면 2박3일 코스 17번에 마른다.
 */
class CarRouteCacheHitTest {

    private static final Coordinate FROM = new Coordinate(37.1, 127.1);

    private static final Coordinate TO = new Coordinate(37.2, 127.2);

    private static final List<Coordinate> POINTS =
            List.of(new Coordinate(37.1, 127.1), new Coordinate(37.2, 127.2), new Coordinate(37.3, 127.3));

    @Test
    void 같은_구간을_두_번_물으면_TMAP_은_한_번만_부른다() {
        AtomicInteger calls = new AtomicInteger();
        CarRouteCacheService cache = CarRouteCaches.remembering();
        RouteTimeProvider provider = new TmapRouteTimeProvider(
                countingRoute(calls, new CarRouteResult.Found(new TmapRoute(42, 30.0))),
                new HaversineTravelTimeProvider(),
                new UnroutableCoordinateService(noProbes()),
                cache);

        assertEquals(42, provider.drivingMinutes(FROM, TO));
        assertEquals(42, provider.drivingMinutes(FROM, TO));

        assertEquals(1, calls.get(), "같은 구간인데 TMAP 을 또 불렀다 — 캐시 키가 어긋났다");
    }

    @Test
    void 같은_좌표_목록을_두_번_물으면_경유지최적화는_한_번만_부른다() {
        AtomicInteger calls = new AtomicInteger();
        RouteOptimizer optimizer =
                new TmapRouteOptimizer(countingOrder(calls, Optional.of(List.of(0, 2, 1))), CarRouteCaches.remembering());

        assertEquals(List.of(0, 2, 1), optimizer.optimalOrder(POINTS));
        assertEquals(List.of(0, 2, 1), optimizer.optimalOrder(POINTS));

        assertEquals(1, calls.get(), "같은 목록인데 또 불렀다 — 한도 50 이 그대로 마른다");
    }

    /**
     * <b>폴백은 캐시하지 않는다.</b>
     *
     * <p>직선거리 정렬은 한도가 말랐을 때 나오는 값이다. 그걸 남기면 <b>하루 한도가 마른 것이 재측정
     * 주기 내내 굳어</b>, 그날 이후로는 한도가 멀쩡해도 계속 직선거리를 쓴다.
     */
    @Test
    void 경유지최적화가_실패하면_캐시하지_않고_다음에_다시_묻는다() {
        AtomicInteger calls = new AtomicInteger();
        RouteOptimizer optimizer =
                new TmapRouteOptimizer(countingOrder(calls, Optional.empty()), CarRouteCaches.remembering());

        optimizer.optimalOrder(POINTS);
        optimizer.optimalOrder(POINTS);

        assertEquals(2, calls.get(), "폴백을 캐시했다 — 한도가 마른 하루가 재측정 주기 내내 굳는다");
    }

    /** 구간 쪽도 같다 — TMAP 이 답을 못 준 것을 남기면 직선거리가 굳는다. */
    @Test
    void 구간_실측이_실패하면_캐시하지_않고_다음에_다시_묻는다() {
        AtomicInteger calls = new AtomicInteger();
        RouteTimeProvider provider = new TmapRouteTimeProvider(
                countingRoute(calls, CarRouteResult.Unavailable.instance()),
                new HaversineTravelTimeProvider(),
                new UnroutableCoordinateService(noProbes()),
                CarRouteCaches.remembering());

        provider.drivingMinutes(FROM, TO);
        provider.drivingMinutes(FROM, TO);

        assertEquals(2, calls.get(), "폴백을 캐시했다 — 한도가 마른 하루가 재측정 주기 내내 굳는다");
    }

    private static TmapClient countingRoute(AtomicInteger calls, CarRouteResult result) {
        return new TmapClient() {
            @Override
            public CarRouteResult carRoute(Coordinate origin, Coordinate destination) {
                calls.incrementAndGet();
                return result;
            }

            @Override
            public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
                throw new UnsupportedOperationException("이 테스트는 순서를 묻지 않는다");
            }
        };
    }

    private static TmapClient countingOrder(AtomicInteger calls, Optional<List<Integer>> order) {
        return new TmapClient() {
            @Override
            public CarRouteResult carRoute(Coordinate origin, Coordinate destination) {
                throw new UnsupportedOperationException("이 테스트는 구간을 묻지 않는다");
            }

            @Override
            public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
                calls.incrementAndGet();
                return order;
            }
        };
    }

    /** 이 테스트는 차단 좌표를 보지 않는다 — 비어 있는 저장소면 충분하다. */
    private static UnroutableProbeRepository noProbes() {
        return new UnroutableProbeRepository() {
            @Override
            public void saveIfAbsent(UnroutableProbe probe) {
            }

            @Override
            public Set<CoordinateKey> pointsWithAtLeast(int minPartners) {
                return Set.of();
            }
        };
    }
}
