package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 열차 이동시간 조회 — transport 가 다른 도메인(itinerary 코스)에 노출하는 공개 서비스. 출발역→도착역·날짜의 가장 빠른 열차를 준다.
 *
 * <p>TAGO 열차정보는 조회가 느리고 쿼터가 있어, 같은 (출발·도착·날짜) 조합을 {@link ExternalDataCache} 로 캐시한다. 시간표는
 * 하루 단위로 고정이라 성공은 길게(6h), 미운행·실패는 짧게(5분) 캐시하고 마지막 성공값을 재사용한다(stale-while-error).
 * 코스 이동시간은 사용자 입력(역·날짜)에 따라 달라져 <b>프리워밍은 불가</b>하다(홈·추천과 달리 요청 시 조회 — ADR 0001 참고).
 */
@Service
@RequiredArgsConstructor
public class TrainRouteService {

    /** 시간표 캐시 TTL — 하루 단위 고정 시간표라 길게 잡아도 신선도 손해가 없다. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 미운행·실패 시 짧은 재시도 TTL. */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    private final TrainInfoClient trainInfoClient;
    private final ExternalDataCache<String, Optional<TrainLeg>> cache = new ExternalDataCache<>();

    /** 출발역→도착역, 해당 날짜의 가장 빠른 열차. 키 없음·실패·미운행 시 빈 Optional. */
    public Optional<TrainLeg> fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        String key = depStationId + "|" + arrStationId + "|" + date;
        return cache.get(key, (k, stale) -> {
            Optional<TrainLeg> fresh = trainInfoClient.fastestTrain(depStationId, arrStationId, date);
            if (fresh.isPresent()) {
                return new Loaded<>(fresh, CACHE_TTL);
            }
            return new Loaded<>(stale != null ? stale : Optional.empty(), EMPTY_TTL);
        }, Optional.empty());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }
}
