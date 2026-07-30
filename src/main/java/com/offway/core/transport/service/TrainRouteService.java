package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 열차 이동시간 조회 — transport 가 다른 도메인(itinerary 코스)에 노출하는 공개 서비스. 출발역→도착역·날짜의 가장 빠른 열차를
 * {@link TrainAvailability}(운행 있음/그 날짜 없음/조회 불가)로 준다.
 *
 * <p>TAGO 열차정보는 조회가 느리고 쿼터가 있어, 같은 (출발·도착·날짜) 조합을 {@link ExternalDataCache} 로 캐시한다. 시간표는
 * 하루 단위로 확정이라 <b>운행 있음·그 날짜 없음</b>은 길게(6h), <b>조회 불가</b>는 짧게(5분) 캐시하고 마지막으로 알던 결과를
 * 재사용한다(stale-while-error). 코스 이동시간은 사용자 입력(역·날짜)에 따라 달라져 <b>프리워밍은 불가</b>하다(ADR 0001).
 */
@Service
@RequiredArgsConstructor
public class TrainRouteService {

    /** 운행 있음·그 날짜 없음 캐시 TTL — 하루 단위 확정 정보라 길게. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 조회 불가(실패) 시 짧은 재시도 TTL. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(5);

    /**
     * 보관할 (출발역·도착역·날짜) 조합 수. <b>키에 날짜가 들어가 키 공간이 시간과 함께 무한히 자란다</b> — 같은 역쌍이라도
     * 날짜가 바뀌면 새 엔트리다. 어제 날짜 엔트리는 다시 쓰이지 않는데 상한이 없으면 영원히 남는다.
     */
    private static final int MAX_CACHED_ROUTES = 2_000;

    private final TrainInfoClient trainInfoClient;
    private final ExternalDataCache<String, TrainAvailability> cache = new ExternalDataCache<>(MAX_CACHED_ROUTES);

    /** 출발역→도착역, 해당 날짜의 가장 빠른 열차 조회 결과. */
    public TrainAvailability fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        String key = depStationId + "|" + arrStationId + "|" + date;
        return cache.get(key, (k, stale) -> {
            TrainAvailability fresh = trainInfoClient.fastestTrain(depStationId, arrStationId, date);
            if (fresh instanceof TrainAvailability.Unavailable) {
                // 조회 실패 — 마지막으로 알던 결과(운행 있음/없음)가 있으면 유지, 없으면 짧게 Unavailable 로 재시도 유도.
                TrainAvailability fallback = stale != null && !(stale instanceof TrainAvailability.Unavailable)
                        ? stale
                        : fresh;
                return new Loaded<>(fallback, RETRY_TTL);
            }
            return new Loaded<>(fresh, CACHE_TTL); // Available·NoServiceOnDate
        }, new TrainAvailability.Unavailable());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }
}
