package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.domain.BusStopAccess;
import com.offway.core.transport.infrastructure.tago.BusStopClient;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대중교통 접근성 조회 — transport 가 다른 도메인(trip·itinerary)에 노출하는 공개 서비스. 좌표 주변에 버스 정류소가 있는지를
 * {@link BusStopAccess}(있음/주변에 없음/조회 불가)로 준다.
 *
 * <p>여행 <b>계획 시점</b>에 쓰는 정보다. 정류소 위치는 잘 바뀌지 않아 미래 날짜 코스에도 유효하다. 반대로 "몇 시에 오는가"는
 * 이 API 가 답하지 못하므로 {@link BusArrivalService}(실시간, 여행 당일)와 역할이 다르다.
 *
 * <p>TAGO 는 느리고 쿼터가 있어 {@link ExternalDataCache} 로 감싼다. 정류소는 거의 안 변하니 길게(24h) 캐시하고, 조회
 * 실패는 짧게(5분) 잡아 재시도를 유도하되 마지막으로 알던 결과를 계속 내려준다(stale-while-error).
 */
@Service
@RequiredArgsConstructor
public class BusAccessService {

    /** 정류소 위치는 거의 변하지 않아 길게 캐시한다. */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /** 조회 불가(실패) 시 짧은 재시도 TTL. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(5);

    /**
     * 캐시 키 좌표 정밀도(소수 4자리 ≈ 11m). 원좌표를 그대로 키로 쓰면 미세하게 다른 좌표마다 키가 생겨 캐시가 사실상
     * 무력화되고 맵이 무한히 커진다.
     */
    private static final String KEY_FORMAT = "%.4f|%.4f";

    private final BusStopClient busStopClient;
    private final ExternalDataCache<String, BusStopAccess> cache = new ExternalDataCache<>();

    /** 좌표 주변 버스 정류소 조회 결과. */
    public BusStopAccess nearbyStops(double lat, double lng) {
        return cache.get(
                cacheKey(lat, lng),
                (key, stale) -> {
                    BusStopAccess fresh = busStopClient.nearbyStops(lat, lng);
                    if (fresh instanceof BusStopAccess.Unavailable) {
                        // 조회 실패 — 마지막으로 알던 결과(있음/없음)가 있으면 유지한다. 정류소는 잘 안 변해 오래된 값도 여전히 맞다.
                        BusStopAccess fallback =
                                stale != null && !(stale instanceof BusStopAccess.Unavailable) ? stale : fresh;
                        return new Loaded<>(fallback, RETRY_TTL);
                    }
                    return new Loaded<>(fresh, CACHE_TTL); // Available·NoStopNearby
                },
                new BusStopAccess.Unavailable());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }

    private static String cacheKey(double lat, double lng) {
        // Locale.ROOT 고정 — 기본 로케일에 따라 소수점이 쉼표가 되면 키가 환경마다 달라진다.
        return String.format(Locale.ROOT, KEY_FORMAT, lat, lng);
    }
}
