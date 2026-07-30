package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지역 콘텐츠(볼거리 수·대표 이미지·categories) 조회 — TourAPI 로 지역별 콘텐츠를 얻고, 볼거리가 부족하면 인접 50km 지역 콘텐츠로
 * 확장한다(F3). TourAPI 는 read-timeout 이 길어 <b>트랜잭션 밖</b>에서 호출한다(persistence-convention).
 *
 * <p>콘텐츠는 89개 고정 지역의 느리게 변하는 값이라 지역별로 인메모리 캐시한다. 조회가 실패하면(TourAPI 타임아웃 등) 콘텐츠는 화면의
 * <b>부가 정보</b>라 예외를 올리지 않고 빈 콘텐츠(이미지·categories 없음)로 degrade 하며, 마지막 성공값이 있으면 재사용한다
 * (stale-while-error). 그래서 홈·추천이 콘텐츠 실패로 502 가 되지 않고, 실패가 이어져도 매 요청이 6초씩 재시도하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionContentProvider {

    /** 콘텐츠 표본 크기 — 대표 이미지·categories 산출용. 볼거리 수는 표본과 무관하게 totalCount 로 온다. */
    private static final int SAMPLE_ROWS = 30;
    /** 콘텐츠 부족 시 묶을 인접 반경(㎞) — feature-spec F3. */
    private static final double NEIGHBOR_RADIUS_KM = 50.0;
    /** 지역 하나당 병합할 인접 지역 상한(호출량 방어). */
    private static final int MAX_NEIGHBORS = 3;
    /** 성공 캐시 TTL — 지역 콘텐츠는 느리게 변한다. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 실패 캐시 TTL — 조회 실패가 이어져도 매 요청이 6초씩 재시도하지 않게 짧게 폴백값을 눌러둔다. */
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);

    /** 보관할 지역 수. 키가 지역 id 라 <b>키 공간이 유한</b>하다 — 인구감소지역 89곳이 전부고, 고시 개정 여유를 얹었다. */
    private static final int MAX_CACHED_REGIONS = 128;

    private final TourApiClient tourApiClient;
    private final ExternalDataCache<Long, RegionContent> cache = new ExternalDataCache<>(MAX_CACHED_REGIONS);

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

    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용(캐시가 이전 시나리오를 물지 않게). */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 지역 콘텐츠 조회 — 캐시(single-flight) 우선. 실패하면 콘텐츠는 부가 정보라 예외를 올리지 않고 마지막 성공값(있으면), 없으면
     * 빈 콘텐츠로 degrade 하고 짧게 캐시해 실패 동안 요청이 몰리지 않게 한다.
     */
    private RegionContent fetch(Region region) {
        return cache.get(region.getId(), (id, stale) -> {
            try {
                RegionContent fresh = tourApiClient
                        .findByArea(region.getAreaCode(), region.getSigunguCode(), null, SAMPLE_ROWS)
                        .toRegionContent();
                return new Loaded<>(fresh, CACHE_TTL);
            } catch (TourApiException e) {
                RegionContent fallback = stale != null ? stale : RegionContent.EMPTY;
                log.warn("지역 콘텐츠 조회 실패 — {} 로 degrade region={}",
                        stale != null ? "마지막 성공값" : "빈 콘텐츠", id, e);
                return new Loaded<>(fallback, FAILURE_CACHE_TTL);
            }
        }, RegionContent.EMPTY);
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
