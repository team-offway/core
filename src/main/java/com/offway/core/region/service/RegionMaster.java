package com.offway.core.region.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.common.geo.Coordinate;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 인구감소지역 89곳 마스터와 그 사이 거리 순서를 <b>부팅 시 한 번</b> 만들어 들고 있는다(#102).
 *
 * <p><b>왜.</b> 입력이 절대 안 바뀌는 계산을 요청마다 다시 하고 있었다.
 *
 * <ul>
 *   <li>{@code regionRepository.findAll()} — 89행 레퍼런스를 추천·홈 요청마다 DB 에서 읽고 엔티티 89개를 만든다.
 *   <li>인접 계산 — 89개 <b>고정 좌표</b>로 haversine 89회 + 정렬을, 볼거리가 부족한 후보마다 다시 한다.
 *       최악 20 × 89 = 1,780회/요청인데 결과가 절대 안 바뀐다.
 * </ul>
 *
 * <p>haversine 자체는 싸다. 문제는 결과가 불변인 계산을 반복한다는 것이고, 이건 "백엔드가 미리 끝내둘 수 있는
 * 일" 의 정의에 그대로 들어맞는다(CLAUDE.md §성능).
 *
 * <p><b>경계.</b> 여기는 "누가 누구에게 가까운가" 라는 지리적 사실만 소유한다. "몇 km 안을 인접으로 볼지,
 * 몇 곳까지 쓸지" 는 쓰는 쪽(trip 의 콘텐츠 보강)의 정책이라 그쪽에 남긴다 — 거리 순서는 정책이 바뀌어도
 * 그대로다.
 *
 * <p><b>갱신은 재기동 전제다.</b> 89곳은 행안부 고시로 정해지고 우리에겐 마이그레이션으로 들어온다. 마이그레이션이
 * 배포와 함께 오므로 그때 프로세스가 새로 뜬다 — 별도 무효화 경로를 두면 쓸 일 없는 코드가 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionMaster {

    private final RegionRepository regionRepository;

    /**
     * 시드가 비었을 때 다시 시도하기까지의 간격.
     *
     * <p>없으면 요청마다 {@code findAll()} 과 warn 한 줄이 나간다. 홈·추천이 각각 부르므로 빈 환경에서는
     * 로그가 요청 수만큼 쌓이고, 정작 봐야 할 다른 warn 이 그 안에 묻힌다.
     */
    private static final Duration EMPTY_RETRY_INTERVAL = Duration.ofMinutes(1);

    /**
     * 지역 목록과 인접 그래프를 <b>한 덩어리로</b> 들고 있는다.
     *
     * <p>둘을 각각 {@code volatile} 로 두고 차례로 대입하면 그 사이에 들어온 스레드가 <b>목록은 새 값인데
     * 그래프는 비어 있는</b> 상태를 본다. 그러면 이웃 조회가 빈 리스트를 돌려주고 콘텐츠 보강이 조용히
     * 건너뛰어진다 — 예외가 안 나서 로그에도 안 남는다. 하나로 묶으면 그 창이 아예 없다.
     */
    private record Snapshot(List<Region> all, Map<Long, List<Neighbor>> neighborsById) {

        private static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());

        private boolean isEmpty() {
            return all.isEmpty();
        }
    }

    /** 워밍 스레드가 쓰고 요청 스레드가 읽는다. 통째로 갈아끼우므로 반쯤 채워진 상태가 보이지 않는다. */
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 마지막 적재 시도 시각(nanoTime). 빈 시드에서 재시도 간격을 재는 데만 쓴다. */
    private volatile long lastAttemptNanos = Long.MIN_VALUE;

    /** 이웃 하나 — 지역과 그 지역까지의 거리(km). 거리를 함께 들어 쓰는 쪽이 자기 반경으로 자를 수 있다. */
    public record Neighbor(Region region, double distanceKm) {
    }

    /**
     * 기동이 끝난 뒤 채운다. 마이그레이션(Flyway)이 시드를 넣은 뒤여야 한다.
     *
     * <p>{@code @PostConstruct} 를 쓰지 않는 이유는 빈 초기화 순서에 기대지 않으려는 것이다. 대신 조회 경로가
     * {@link #ensureLoaded()} 로 스스로 채우므로, 이 이벤트보다 요청이 먼저 와도 빈 결과가 나가지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        reload();
    }

    /** 전체 지역(불변). 순서는 저장소가 준 순서 그대로다. */
    public List<Region> all() {
        return ensureLoaded().all();
    }

    /**
     * 이 지역에서 가까운 순의 다른 지역들 — <b>전부</b>. 자르는 것은 쓰는 쪽 몫이다.
     *
     * @return 없는 지역이면 빈 리스트
     */
    public List<Neighbor> neighborsOf(long regionId) {
        return ensureLoaded().neighborsById().getOrDefault(regionId, List.of());
    }

    /**
     * 반경·개수까지 잘라서 준다 — 요청 경로가 흔히 쓰는 모양.
     *
     * <p>거리 순으로 정렬돼 있으므로 반경을 넘는 것을 만나면 그 뒤는 볼 필요가 없다.
     */
    public List<Region> neighborsWithin(long regionId, double radiusKm, int limit) {
        return neighborsOf(regionId).stream()
                .takeWhile(neighbor -> neighbor.distanceKm() <= radiusKm)
                .limit(limit)
                .map(Neighbor::region)
                .toList();
    }

    /**
     * 비었으면 채우고 지금 스냅샷을 준다.
     *
     * <p>이중 채움을 막지 않는다. 두 스레드가 동시에 들어와도 각자 같은 결과를 만들어 같은 값으로 덮을 뿐이라
     * 결과가 어긋나지 않고, 락을 걸면 89행 조회 하나 때문에 요청 스레드가 서로를 기다린다.
     *
     * <p><b>빈 채로 돌아오면 간격을 두고 다시 시도한다.</b> 시드가 없는 환경에서 매 요청 DB 를 읽고 warn 을
     * 남기면, 그 로그가 요청 수만큼 쌓여 정작 봐야 할 경고를 묻는다.
     */
    private Snapshot ensureLoaded() {
        Snapshot current = snapshot;
        if (!current.isEmpty() || !retryDue()) {
            return current;
        }
        return reload();
    }

    private boolean retryDue() {
        long last = lastAttemptNanos;
        return last == Long.MIN_VALUE || System.nanoTime() - last >= EMPTY_RETRY_INTERVAL.toNanos();
    }

    private Snapshot reload() {
        lastAttemptNanos = System.nanoTime();
        List<Region> loaded = regionRepository.findAll().stream()
                .filter(RegionMaster::hasCoordinate)
                .toList();
        if (loaded.isEmpty()) {
            // 시드가 아직 없는 상태(초기 부팅)다. 예외로 부팅을 막지 않는다 — 조회가 비면 그 화면만 빈다.
            log.warn("좌표를 가진 지역이 없어 마스터를 채우지 못했습니다. {} 뒤에 다시 시도합니다",
                    EMPTY_RETRY_INTERVAL);
            return snapshot;
        }
        Snapshot filled = new Snapshot(loaded, loaded.stream().collect(Collectors.toUnmodifiableMap(
                Region::getId, region -> sortedNeighbors(region, loaded))));

        snapshot = filled;
        log.info("지역 마스터 적재 완료 지역={} 인접그래프={}", loaded.size(), filled.neighborsById().size());
        return filled;
    }

    /** 한 지역에서 나머지 전부를 가까운 순으로. 89 × 88 이라 부팅 때 한 번이면 충분하다. */
    private static List<Neighbor> sortedNeighbors(Region region, List<Region> pool) {
        Coordinate center = region.coordinate();
        return pool.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), region.getId()))
                .map(candidate -> new Neighbor(
                        candidate, center.haversineKmTo(candidate.coordinate())))
                .sorted(Comparator.comparingDouble(Neighbor::distanceKm))
                .toList();
    }

    /** 좌표가 없는 지역은 거리 계산에 못 들어간다. 시드에는 전부 있지만 마스터가 스스로 보장한다. */
    private static boolean hasCoordinate(Region region) {
        return region.getLat() != null && region.getLng() != null;
    }
}
