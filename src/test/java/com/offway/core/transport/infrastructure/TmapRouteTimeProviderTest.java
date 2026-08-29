package com.offway.core.transport.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.CoordinateKey;
import com.offway.core.transport.domain.UnroutableProbe;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.infrastructure.tmap.TmapClient;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import com.offway.core.transport.repository.UnroutableProbeRepository;
import com.offway.core.transport.service.RouteTimeProvider;
import com.offway.core.transport.service.UnroutableCoordinateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TmapRouteTimeProviderTest {

    private static final Coordinate FROM = new Coordinate(37.5665, 126.9780);
    private static final Coordinate TO = new Coordinate(35.1796, 129.0756);

    /** carRoute 만 정해 주는 stub — optimizeCarOrder 는 이 테스트와 무관해 빈 값. */
    private static TmapClient carRouteStub(CarRouteResult result) {
        return new TmapClient() {
            @Override
            public CarRouteResult carRoute(Coordinate origin, Coordinate destination) {
                return result;
            }

            @Override
            public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
                return Optional.empty();
            }
        };
    }

    /** 기록된 구간을 그대로 들고 있는 저장소 — 무엇이 남았는지 본문에서 바로 확인한다. */
    private static final class RecordingRepository implements UnroutableProbeRepository {

        private final List<UnroutableProbe> saved = new ArrayList<>();

        @Override
        public void saveIfAbsent(UnroutableProbe probe) {
            saved.add(probe);
        }

        @Override
        public Set<CoordinateKey> pointsWithAtLeast(int minPartners) {
            return Set.of();
        }
    }

    private static TmapRouteTimeProvider provider(CarRouteResult result, UnroutableProbeRepository repository) {
        return new TmapRouteTimeProvider(
                carRouteStub(result), new HaversineTravelTimeProvider(),
                new UnroutableCoordinateService(repository));
    }

    @Test
    void TMAP_실측이_있으면_그_시간을_쓴다() {
        RouteTimeProvider timeProvider =
                provider(new CarRouteResult.Found(new TmapRoute(42, 30.0)), new RecordingRepository());

        assertEquals(42, timeProvider.drivingMinutes(FROM, TO));
    }

    @Test
    void TMAP가_없으면_직선거리_근사로_폴백한다() {
        RouteTimeProvider timeProvider =
                provider(CarRouteResult.Unavailable.instance(), new RecordingRepository());

        assertTrue(timeProvider.drivingMinutes(FROM, TO) > 0); // Haversine 폴백값(서울~부산은 수 시간대)
    }

    /**
     * 타임아웃·한도 소진은 <b>다음에 성공할 수 있다.</b> 그걸로 좌표를 기록하면 멀쩡한 장소가 영구히
     * 코스에서 사라진다 — 폴백만 하고 아무것도 기억하지 않아야 한다.
     */
    @Test
    void 좌표_탓이_아닌_실패는_기록하지_않는다() {
        RecordingRepository repository = new RecordingRepository();

        provider(CarRouteResult.Unavailable.instance(), repository).drivingMinutes(FROM, TO);

        assertTrue(repository.saved.isEmpty());
    }

    /** 좌표 탓이면 <b>양쪽 다</b> 적는다 — 어느 쪽이 나쁜지는 이 한 건으로 못 가른다. */
    @Test
    void 좌표_탓인_거절은_양쪽을_짝과_함께_기록한다() {
        RecordingRepository repository = new RecordingRepository();

        int minutes = provider(new CarRouteResult.Rejected(UnroutableReason.NO_ROAD_LINK), repository)
                .drivingMinutes(FROM, TO);

        assertTrue(minutes > 0, "거절돼도 직선거리로는 값을 준다 — 코스가 끊기면 안 된다");
        assertEquals(2, repository.saved.size());
        assertEquals(CoordinateKey.of(FROM), repository.saved.get(0).point());
        assertEquals(CoordinateKey.of(TO), repository.saved.get(0).partner());
        assertEquals(CoordinateKey.of(TO), repository.saved.get(1).point());
        assertEquals(CoordinateKey.of(FROM), repository.saved.get(1).partner());
    }
}
