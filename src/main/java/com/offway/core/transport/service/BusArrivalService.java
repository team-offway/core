package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.infrastructure.tago.BusArrivalClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 실시간 버스 도착 조회 — transport 가 다른 도메인에 노출하는 공개 서비스. 여행 <b>당일</b> "다음 버스가 언제 오나"에 답한다.
 * 계획 시점의 대중교통 판단은 {@link BusAccessService}(정류소 유무)가 맡는다.
 *
 * <p><b>여기서는 stale-while-error 를 쓰지 않는다.</b> 캐시 프리미티브가 그 기능을 주지만, 실시간 값에 적용하면 해로운
 * 정보가 된다 — 10분 전에 받은 "3분 후 도착"을 그대로 내려주면 이미 떠난 버스를 기다리게 만든다. 느리게 변하는 값(정류소 위치·
 * 방문자수)과 달리, 오래된 도착 정보는 없느니만 못하다. 그래서 실패하면 stale 대신 조회 불가를 돌려준다.
 *
 * <p>캐시를 아예 안 쓰지는 않는다. 같은 정류소를 여러 사용자가 동시에 볼 때 쿼터가 터지지 않게 아주 짧게(20초)만 묶는다.
 */
@Service
@RequiredArgsConstructor
public class BusArrivalService {

    /** 실시간 값이라 아주 짧게만 묶는다 — 동시 요청 폭주로 쿼터가 소진되는 것만 막는 수준. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(20);

    /** 조회 실패도 짧게 잡아 곧 재시도되게 한다. */
    private static final Duration RETRY_TTL = Duration.ofSeconds(20);

    private final BusArrivalClient busArrivalClient;
    private final ExternalDataCache<String, BusArrivalStatus> cache = new ExternalDataCache<>();

    /** 정류소의 실시간 도착 예정 버스. */
    public BusArrivalStatus arrivalsAt(BusStop stop) {
        String key = stop.cityCode() + "|" + stop.nodeId();
        return cache.get(
                key,
                (k, stale) -> {
                    BusArrivalStatus fresh = busArrivalClient.arrivalsAt(stop);
                    // stale 을 폴백으로 쓰지 않는다(위 javadoc 참고). 실패는 실패대로 짧게 캐시해 재시도를 유도한다.
                    Duration ttl = fresh instanceof BusArrivalStatus.Unavailable ? RETRY_TTL : CACHE_TTL;
                    return new Loaded<>(fresh, ttl);
                },
                new BusArrivalStatus.Unavailable());
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }
}
