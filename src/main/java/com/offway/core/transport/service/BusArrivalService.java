package com.offway.core.transport.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.cache.ExternalDataCache.StalePolicy;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCachePolicy;
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
 * <p><b>여기서는 stale 을 절대 내리지 않는다.</b> 캐시 프리미티브가 stale 재사용을 주지만, 실시간 값에 적용하면 해로운
 * 정보가 된다 — 10분 전에 받은 "3분 후 도착"을 그대로 내려주면 이미 떠난 버스를 기다리게 만든다. 느리게 변하는 값(정류소 위치·
 * 방문자수)과 달리, 오래된 도착 정보는 없느니만 못하다. 그래서 {@link StalePolicy#DISALLOW_STALE} 로 호출해,
 * <b>재조회 실패든 다른 스레드가 갱신 중이든</b> stale 대신 조회 불가를 돌려준다.
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

    /**
     * 보관할 정류소 수. 키가 정류소라 <b>키 공간이 전국 정류소 수</b>(수만)다. TTL 이 20초로 짧아 값은 금방 죽지만
     * <b>엔트리는 남으므로 회전이 빠를수록 오히려 더 빨리 쌓인다</b> — 상한이 없으면 조회량에 비례해 힙이 자란다.
     */
    private static final int MAX_CACHED_STOPS = 2_000;

    /** loader 가 TAGO 도착정보 <b>단일 호출</b>(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final BusArrivalClient busArrivalClient;

    /** 캐시를 켜고 끄는 스위치(#403). 조회마다 물어, 운영 중 바뀐 값도 곧바로 듣는다. */
    private final ExternalApiCachePolicy cachePolicy;
    private final ExternalDataCache<String, BusArrivalStatus> cache = new ExternalDataCache<>(MAX_CACHED_STOPS, FIRST_LOAD_WAIT, this::cacheEnabled);

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
                new BusArrivalStatus.Unavailable(),
                StalePolicy.DISALLOW_STALE); // 실시간 값 — 갱신 중 동시 요청에도 stale(만료된 도착정보) 대신 조회 불가를 준다
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 통합 테스트 격리용. */
    public void evictCache() {
        cache.evictAll();
    }

    /**
     * 캐시를 지금 써도 되나(#403).
     *
     * <p>람다로 필드를 직접 읽지 않고 메서드 참조를 쓰는 이유 — 캐시 필드의 초기화식은 생성자가
     * {@code cachePolicy} 를 넣기 <b>전에</b> 돌아서, 거기서 blank final 을 읽으면 컴파일이 막힌다.
     * 메서드 본문은 그때 읽히지 않는다.
     */
    private boolean cacheEnabled() {
        return cachePolicy.cacheEnabled(ExternalApi.BUS_ARRIVAL);
    }
}
