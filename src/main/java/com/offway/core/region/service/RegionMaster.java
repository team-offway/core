package com.offway.core.region.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
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
     * 전체 지역. {@code volatile} 인 이유는 워밍 스레드가 쓰고 요청 스레드가 읽기 때문이다.
     *
     * <p>불변 리스트를 통째로 갈아끼우므로 부분적으로 채워진 상태가 보이지 않는다.
     */
    private volatile List<Region> all = List.of();

    /** {@code regionId → 가까운 순으로 정렬된 다른 지역들}. 자기 자신은 빠져 있다. */
    private volatile Map<Long, List<Neighbor>> neighborsById = Map.of();

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
        ensureLoaded();
        return all;
    }

    /**
     * 이 지역에서 가까운 순의 다른 지역들 — <b>전부</b>. 자르는 것은 쓰는 쪽 몫이다.
     *
     * @return 없는 지역이면 빈 리스트
     */
    public List<Neighbor> neighborsOf(long regionId) {
        ensureLoaded();
        return neighborsById.getOrDefault(regionId, List.of());
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
     * 비었으면 채운다.
     *
     * <p>이중 채움을 막지 않는다. 두 스레드가 동시에 들어와도 각자 같은 결과를 만들어 같은 값으로 덮을 뿐이라
     * 결과가 어긋나지 않고, 락을 걸면 89행 조회 하나 때문에 요청 스레드가 서로를 기다린다.
     */
    private void ensureLoaded() {
        if (all.isEmpty()) {
            reload();
        }
    }

    private void reload() {
        List<Region> loaded = regionRepository.findAll().stream()
                .filter(RegionMaster::hasCoordinate)
                .toList();
        if (loaded.isEmpty()) {
            // 시드가 아직 없는 상태(테스트·초기 부팅)다. 예외로 부팅을 막지 않는다 — 조회가 비면 그 화면만 빈다.
            log.warn("좌표를 가진 지역이 없어 마스터를 채우지 못했습니다");
            return;
        }
        Map<Long, List<Neighbor>> graph = loaded.stream().collect(Collectors.toUnmodifiableMap(
                Region::getId, region -> sortedNeighbors(region, loaded)));

        all = loaded;
        neighborsById = graph;
        log.info("지역 마스터 적재 완료 지역={} 인접그래프={}", loaded.size(), graph.size());
    }

    /** 한 지역에서 나머지 전부를 가까운 순으로. 89 × 88 이라 부팅 때 한 번이면 충분하다. */
    private static List<Neighbor> sortedNeighbors(Region region, List<Region> pool) {
        Coordinate center = new Coordinate(region.getLat(), region.getLng());
        return pool.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), region.getId()))
                .map(candidate -> new Neighbor(
                        candidate, center.haversineKmTo(new Coordinate(candidate.getLat(), candidate.getLng()))))
                .sorted(Comparator.comparingDouble(Neighbor::distanceKm))
                .toList();
    }

    /** 좌표가 없는 지역은 거리 계산에 못 들어간다. 시드에는 전부 있지만 마스터가 스스로 보장한다. */
    private static boolean hasCoordinate(Region region) {
        return region.getLat() != null && region.getLng() != null;
    }
}
