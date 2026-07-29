package com.offway.core.common.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;

/**
 * 느리고 불안정한 외부 API 응답을 감싸는 재사용 캐시 프리미티브. OffWay 의 외부 의존(관광빅데이터·TourAPI·에어코리아 등)은
 * 지연·쿼터·간헐 실패가 있어, 요청 경로에서 직접 부르면 느리고 쿼터가 금방 소진된다. 이 캐시는 그 사고를 한 곳에 담는다.
 *
 * <p>담긴 규율:
 * <ul>
 *   <li><b>키별 TTL 캐시</b> — 값마다 신선도 기간을 달리 준다(성공은 길게, 실패·빈 값은 짧게).
 *   <li><b>single-flight</b> — 만료 순간 같은 키에 요청이 몰려도 한 스레드만 재조회하고, 나머지는 stale 값(없으면 폴백)을
 *       즉시 받는다. 캐시 스탬피드로 외부를 동시 다발 호출하는 것을 막는다. 잠금은 외부 I/O 동안 잡지 않는다.
 *   <li><b>double-check</b> — 게이트 획득 직후 캐시를 재확인해, 획득 사이 다른 스레드가 이미 갱신했으면 그 값을 쓴다(직렬 중복 호출 방지).
 *   <li><b>stale-while-error</b> — 재조회 실패 시 마지막 성공값을 계속 내려준다. 폴백·실패 TTL 선택은 loader 가 소유한다
 *       (외부 예외 타입을 이 프리미티브가 알 필요가 없다).
 * </ul>
 *
 * <p><b>단일 인스턴스 전제.</b> JVM 인메모리라 인스턴스를 늘리면 히트율이 나뉘고 각자 워밍·single-flight 를 돌려 외부 호출이
 * 인스턴스 수만큼 는다. 공모전 규모(단일 인스턴스)에선 로컬 캐시가 더 빠르고 단순해 의식적으로 택했고, 스케일아웃하면 동일 인터페이스로
 * 공유 캐시(Redis)에 옮긴다.
 *
 * @param <K> 캐시 키(예: 지역 id·시도명). 단일 값이면 상수 키 하나를 쓴다.
 * @param <V> 캐시 값
 */
@Slf4j
public final class ExternalDataCache<K, V> {

    private final Map<K, Entry<V>> cache = new ConcurrentHashMap<>();
    private final Set<K> refreshing = ConcurrentHashMap.newKeySet();

    /**
     * 키의 신선한 값을 주거나, 만료됐으면 single-flight 로 재조회한다.
     *
     * @param key 캐시 키
     * @param loader {@code (key, stale)} → 새 값과 그 TTL. 두 번째 인자는 현재 stale 값(없으면 null)이라, loader 가 조회
     *     실패 시 stale 을 그대로 폴백으로 돌려줄 수 있다. loader 는 외부 예외를 스스로 잡아 폴백 값을 반환한다.
     *     <b>{@link StalePolicy#DISALLOW_STALE} 로 호출하면 이 인자는 항상 {@code null}</b> 이다(아래 참고).
     * @param noStaleFallback 재조회를 다른 스레드가 진행 중인데 stale 도 없을 때 즉시 돌려줄 값(첫 동시 요청용)
     * @return 신선하거나 stale 한 값, 또는 폴백
     */
    public V get(K key, BiFunction<K, V, Loaded<V>> loader, V noStaleFallback) {
        return get(key, loader, noStaleFallback, StalePolicy.ALLOW_STALE);
    }

    /**
     * {@link #get(Object, BiFunction, Object)} 와 같되, stale 재사용 정책을 고른다.
     *
     * <p>정책은 <b>stale 이 샐 수 있는 세 통로</b>(갱신 중 동시 요청 · loader 예외 · loader 의 stale 반환)에 함께
     * 적용된다. 무엇을 내릴지는 {@link StalePolicy} 가 스스로 안다.
     *
     * @param stalePolicy stale 재사용 정책 — 각 상수의 문서 참고
     */
    public V get(K key, BiFunction<K, V, Loaded<V>> loader, V noStaleFallback, StalePolicy stalePolicy) {
        Objects.requireNonNull(stalePolicy, "stalePolicy");
        Entry<V> cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.value();
        }
        // single-flight: 이 키를 이미 다른 스레드가 갱신 중이면 stale(없거나 미제공이면 폴백)을 즉시 반환한다.
        if (!refreshing.add(key)) {
            return stalePolicy.degrade(cached != null ? cached.value() : null, noStaleFallback);
        }
        try {
            // double-check: 게이트 획득 사이 다른 스레드가 이미 갱신했을 수 있다.
            Entry<V> latest = cache.get(key);
            if (latest != null && latest.isFresh()) {
                return latest.value();
            }
            V stale = latest != null ? latest.value() : (cached != null ? cached.value() : null);
            Loaded<V> loaded;
            try {
                // stale 을 안 쓰는 정책이면 loader 에게 아예 쥐여주지 않는다 — loader 가 그걸 새 값으로 되돌려주는 우회로를
                // 구조적으로 막는다(협조에 기대지 않는다).
                loaded = loader.apply(key, stalePolicy.staleForLoader(stale));
            } catch (RuntimeException e) {
                // 최후 방어선 — loader 는 외부 예외를 스스로 잡는 게 계약이지만, 그 계약이 깨져도(예외 누수) 요청 경로로 올리지
                // 않는다. stale 을 쓰는 값이면 그걸로, 아니면 폴백으로 degrade 한다. 캐시엔 넣지 않아 다음 호출이 재시도한다.
                log.warn("캐시 loader 가 예외를 던졌습니다 — degrade key={}", key, e);
                return stalePolicy.degrade(stale, noStaleFallback);
            }
            cache.put(key, new Entry<>(loaded.value(), Instant.now().plus(loaded.ttl())));
            return loaded.value();
        } finally {
            refreshing.remove(key);
        }
    }

    /** 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용. */
    public void evictAll() {
        cache.clear();
    }

    /**
     * 신선한 값을 못 줄 때 stale 을 재사용할지의 정책. 무엇을 내릴지는 각 상수가 스스로 안다(호출부에 분기가 없다).
     *
     * <p>stale 이 샐 수 있는 통로는 <b>세 곳</b>이고, 정책이 전부 소유한다 — ① 다른 스레드가 갱신 중일 때(single-flight 에
     * 막힌 요청) ② loader 가 계약을 어기고 예외를 던졌을 때 ③ loader 가 조회 실패 시 stale 을 새 값으로 되돌려줄 때.
     * ①·② 는 {@link #degrade}, ③ 은 {@link #staleForLoader} 가 막는다. 세 곳을 한 정책이 쥐고 있어야
     * {@code DISALLOW_STALE} 이 이름값을 한다.
     */
    public enum StalePolicy {

        /** 느리게 변하는 값(정류소·방문자수)용 — stale 이 있으면 재사용해 스탬피드를 막는다. */
        ALLOW_STALE {
            @Override
            <V> V degrade(V stale, V fallback) {
                return stale != null ? stale : fallback;
            }

            @Override
            <V> V staleForLoader(V stale) {
                return stale; // loader 가 조회 실패 시 폴백으로 쓸 수 있게 넘긴다
            }
        },

        /**
         * 실시간 값(버스 도착)용 — stale 을 절대 내리지 않는다. 10분 전에 받은 "3분 후 도착"은 이미 떠난 버스를 기다리게
         * 만들어, 없느니만 못한 정보다. loader 에게도 stale 을 넘기지 않아, 되돌려줄 수단 자체가 없다.
         */
        DISALLOW_STALE {
            @Override
            <V> V degrade(V stale, V fallback) {
                return fallback;
            }

            @Override
            <V> V staleForLoader(V stale) {
                return null;
            }
        };

        /** 신선한 값이 없을 때 무엇을 내릴지 — 정책이 고른다. */
        abstract <V> V degrade(V stale, V fallback);

        /** loader 에게 보여줄 stale — 재사용을 막는 정책이면 감춘다. */
        abstract <V> V staleForLoader(V stale);
    }

    /** loader 가 돌려주는 결과 — 값과 그 값을 캐시할 기간. 성공/실패·빈 값에 따라 TTL 을 달리 골라 넘긴다. */
    public record Loaded<V>(V value, Duration ttl) {}

    private record Entry<V>(V value, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
