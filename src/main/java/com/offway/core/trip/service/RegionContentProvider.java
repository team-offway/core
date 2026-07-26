package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지역 콘텐츠(볼거리 수·대표 이미지·categories) 조회 — TourAPI 로 지역별 콘텐츠를 얻고, 볼거리가 부족하면 인접 50km 지역 콘텐츠로
 * 확장한다(F3). TourAPI 는 read-timeout 이 길어 <b>트랜잭션 밖</b>에서 호출한다(persistence-convention). 호출량은 반환 후보로
 * 한정하고, 확장·상한 도달을 로깅한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RegionContentProvider {

    /** 콘텐츠 표본 크기 — 대표 이미지·categories 산출용. 볼거리 수는 표본과 무관하게 totalCount 로 온다. */
    private static final int SAMPLE_ROWS = 30;
    /** 콘텐츠 부족 시 묶을 인접 반경(㎞) — feature-spec F3. */
    private static final double NEIGHBOR_RADIUS_KM = 50.0;
    /** 지역 하나당 병합할 인접 지역 상한(호출량 방어). */
    private static final int MAX_NEIGHBORS = 3;

    private final TourApiClient tourApiClient;

    /**
     * 한 지역의 콘텐츠. 볼거리가 충분하면 그대로, 부족하면 인접 50km 지역(가까운 순, 최대 {@value #MAX_NEIGHBORS}곳)을 충분해질
     * 때까지 병합한다. {@code neighborPool} 은 인접 후보(보통 전체 지역) — 자기 자신은 자동 제외한다.
     */
    RegionContent contentFor(Region region, List<Region> neighborPool) {
        RegionContent content = fetch(region);
        if (content.isSufficient()) {
            return content;
        }
        for (Region neighbor : nearestNeighbors(region, neighborPool)) {
            content = content.expandedWith(fetch(neighbor));
            if (content.isSufficient()) {
                break;
            }
        }
        if (content.neighborIncluded()) {
            log.info("콘텐츠 확장 region={} → contentCount={} categories={}",
                    region.getId(), content.contentCount(), content.categories().size());
        }
        return content;
    }

    private RegionContent fetch(Region region) {
        return tourApiClient
                .findByArea(region.getAreaCode(), region.getSigunguCode(), null, SAMPLE_ROWS)
                .toRegionContent();
    }

    /** 반경 50km 안의 다른 지역을 가까운 순으로 상한만큼. */
    private List<Region> nearestNeighbors(Region region, List<Region> pool) {
        Coordinate center = new Coordinate(region.getLat(), region.getLng());
        return pool.stream()
                .filter(candidate -> !candidate.getId().equals(region.getId()))
                .map(candidate -> Map.entry(
                        candidate, center.haversineKmTo(new Coordinate(candidate.getLat(), candidate.getLng()))))
                .filter(entry -> entry.getValue() <= NEIGHBOR_RADIUS_KM)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(MAX_NEIGHBORS)
                .map(Map.Entry::getKey)
                .toList();
    }
}
