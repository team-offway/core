package com.offway.core.weather.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.domain.SidoName;
import com.offway.core.weather.infrastructure.airkorea.AirKoreaClient;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대기질 조회 — weather 가 다른 도메인(trip 지역·itinerary 코스)에 노출하는 공개 서비스. 지역 시도명으로 실시간 대기질 요약을 준다.
 * 외부(에어코리아) 호출은 부가 정보라 실패 시 빈 값.
 *
 * <p>에어코리아는 시도당 조회가 수 초 걸려 홈이 top-N 시도를 매번 부르면 느려진다. 미세먼지는 시간당 갱신되므로 시도별로 짧게 캐시해
 * 요청 경로에서 반복 호출을 없앤다(값 있으면 1시간, 없으면 5분 뒤 재시도). 캐시·single-flight 는 {@link ExternalDataCache}
 * 가 담당한다. 홈 캐시 워밍이 이 캐시를 미리 채운다.
 */
@Service
@RequiredArgsConstructor
public class AirQualityService {

    /** 대기질 캐시 TTL — 미세먼지는 시간당 발표라 1시간이면 신선도 손해가 없다. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    /** 값이 없을 때(키 없음·조회 실패) 재시도까지의 짧은 TTL. */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    /** 보관할 시도 수. 키가 시도명이라 <b>키 공간이 유한</b>하다 — 17개 시도가 전부고, 표기 변형 여유를 얹었다. */
    private static final int MAX_CACHED_SIDO = 32;

    /** loader 가 에어코리아 <b>단일 호출</b>(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final AirKoreaClient airKoreaClient;
    private final ExternalDataCache<String, Optional<AirQuality>> cache = new ExternalDataCache<>(MAX_CACHED_SIDO, FIRST_LOAD_WAIT);

    /** 지역 시도명(정식/축약 모두)에 대한 실시간 대기질. 없으면 빈 Optional. 시도별로 캐시한다. */
    public Optional<AirQuality> byRegionSido(String sido) {
        String key = SidoName.toShort(sido);
        return cache.get(key, (k, stale) -> {
            Optional<AirQuality> fresh = airKoreaClient.realtimeBySido(k);
            if (fresh.isPresent()) {
                return new Loaded<>(fresh, CACHE_TTL);
            }
            // 값 없음(조회 실패·미제공) — 직전 정상값이 있으면 유지(stale-while-error), 없으면 짧게 빈 값 뒤 재시도.
            return new Loaded<>(stale != null ? stale : Optional.empty(), EMPTY_TTL);
        }, Optional.empty());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }
}
