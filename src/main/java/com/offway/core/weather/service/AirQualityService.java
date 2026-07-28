package com.offway.core.weather.service;

import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.domain.SidoName;
import com.offway.core.weather.infrastructure.airkorea.AirKoreaClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기질 조회 — weather 가 다른 도메인(trip 지역·itinerary 코스)에 노출하는 공개 서비스. 지역 시도명으로 실시간 대기질 요약을 준다.
 * 외부(에어코리아) 호출은 부가 정보라 실패 시 빈 값.
 *
 * <p>에어코리아는 시도당 조회가 수 초 걸려 홈이 top-N 시도를 매번 부르면 느려진다. 미세먼지는 시간당 갱신되므로 시도별로 짧게 캐시해
 * 요청 경로에서 반복 호출을 없앤다(값 있으면 1시간, 없으면 5분 뒤 재시도). 홈 캐시 워밍이 이 캐시를 미리 채운다.
 */
@Service
@RequiredArgsConstructor
public class AirQualityService {

    /** 대기질 캐시 TTL — 미세먼지는 시간당 발표라 1시간이면 신선도 손해가 없다. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    /** 값이 없을 때(키 없음·조회 실패) 재시도까지의 짧은 TTL. */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    private final AirKoreaClient airKoreaClient;
    private final Map<String, CachedAir> cache = new ConcurrentHashMap<>();
    /** 시도별 single-flight 게이트 — 만료 시 시도당 한 스레드만 재조회하고 나머지는 stale 값을 즉시 받는다. */
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    /** 지역 시도명(정식/축약 모두)에 대한 실시간 대기질. 없으면 빈 Optional. 시도별로 캐시한다. */
    public Optional<AirQuality> byRegionSido(String sido) {
        String key = SidoName.toAirKorea(sido);
        CachedAir cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.value();
        }
        // single-flight: 이 시도를 이미 다른 스레드가 갱신 중이면 stale 값(없으면 빈 Optional)을 즉시 반환해 중복 외부 호출을 막는다.
        if (!refreshing.add(key)) {
            return cached != null ? cached.value() : Optional.empty();
        }
        try {
            Optional<AirQuality> fresh = airKoreaClient.realtimeBySido(key);
            cache.put(key, CachedAir.of(fresh, fresh.isPresent() ? CACHE_TTL : EMPTY_TTL));
            return fresh;
        } finally {
            refreshing.remove(key);
        }
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용. */
    public void evictCache() {
        cache.clear();
    }

    /** 시도별 대기질 캐시 한 칸 — 만료 시각까지 재사용. Optional·AirQuality 는 불변이라 공유해도 안전. */
    private record CachedAir(Optional<AirQuality> value, Instant expiresAt) {
        static CachedAir of(Optional<AirQuality> value, Duration ttl) {
            return new CachedAir(value, Instant.now().plus(ttl));
        }

        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
