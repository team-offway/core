package com.offway.core.common.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;

/**
 * 느리고 불안정한 외부 API 응답을 감싸는 재사용 캐시 프리미티브. OffWay 의 외부 의존(관광빅데이터·TourAPI·기상청 등)은
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
 *   <li><b>엔트리 수 상한</b> — 아래 참고.
 * </ul>
 *
 * <h2>왜 상한이 필요한가</h2>
 *
 * <b>TTL 은 값의 신선도만 관리하고 엔트리를 지우지 않는다.</b> 만료된 엔트리도 맵에 그대로 남아 자리를 차지하므로, 키 공간이
 * 무한하면(좌표·정류소 id·날짜가 섞인 키) 트래픽에 비례해 힙이 계속 자란다. TTL 이 짧을수록 회전이 빨라 <b>오히려 더 빨리</b>
 * 쌓인다. 그래서 소유자가 {@code maxEntries} 로 "이 캐시의 키는 몇 개까지 늘 수 있나" 를 반드시 답하게 한다(성능 규약
 * "캐시 키 공간의 상한을 먼저 정한다").
 *
 * <p><b>축출 순서</b>: ① 만료된 엔트리 — 값이 이미 죽었는데 자리만 차지한다. ② 그래도 넘치면 <b>가장 먼저 만료될</b>
 * 엔트리부터. 어차피 곧 버릴 것이고, 접근 시각 같은 추가 상태 없이 정할 수 있다. TTL 이 균일한 캐시에서는 사실상 가장 오래된
 * 것부터 나가는 것과 같다.
 *
 * <p><b>Caffeine 을 쓰지 않은 이유</b>: 이 프리미티브의 single-flight 는 <b>막지 않는</b> 방식이다 — 갱신 중인 키에
 * 들어온 요청을 기다리게 하지 않고 stale 을 즉시 준다. Caffeine 의 로딩 캐시는 같은 키를 기다리게 하고, stale 은 만료와
 * 함께 사라져 loader 에게 넘길 수 없다. 그 두 성질이 이 캐시의 존재 이유라, 라이브러리에 맞추려면 오히려 의미를 잃는다.
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

    /**
     * 지금 적재 중인 키 → 그 결과를 받을 future.
     *
     * <p>{@code Set} 이 아니라 future 인 이유 — <b>캐시가 빈 상태에서 동시 요청</b>이 오면 늦은 쪽에게 줄 stale 이
     * 없다. 그때 폴백을 즉시 주면 한 명은 정상 값을, 다른 한 명은 실패를 받는다. 이 future 로 결과를 나눠 갖는다.
     */
    private final Map<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * 무효화 세대 — {@link #evictAll()} 이 올린다.
     *
     * <p><b>무효화가 저장된 값만 비우면 옛 값이 되살아난다.</b> 로드가 시작된 뒤 무효화하면, 그 로드가
     * 끝나면서 무효화 이전의 값을 다시 넣어 버린다. "강제 갱신했는데 안 바뀐다" 가 되고, 그때 원인을
     * 캐시가 아니라 데이터나 외부 API 에서 찾기 시작하면 시간을 크게 버린다(#215).
     *
     * <p>그래서 로드를 시작할 때 세대를 적어 두고, 저장하기 직전에 같은 세대인지 본다. 다르면 그 결과는
     * 이미 무효화된 세계의 값이라 버린다.
     */
    private final AtomicLong generation = new AtomicLong();

    /**
     * 세대 증가·맵 조작을 한 덩어리로 묶는 잠금.
     *
     * <p>세대만 비교하면 "같은 세대임을 확인한 직후 무효화되고, 그 뒤에 저장" 하는 창이 남는다. 그 창을
     * 닫으려면 비교와 저장이 무효화와 겹치지 않아야 한다.
     *
     * <p><b>외부 I/O 동안에는 잡지 않는다.</b> 맵을 넣고 지우는 순간에만 잡으므로, "잠금을 들고 외부를
     * 기다리지 않는다" 는 이 캐시의 성질은 그대로다.
     */
    private final Object evictLock = new Object();

    private final int maxEntries;
    private final Duration maxWaitForFirstLoad;

    /**
     * @param maxEntries 보관할 최대 엔트리 수. <b>소유자가 키 공간의 상한을 직접 답해야 한다</b> — 유한한 키(지역 89개·
     *     시도 17개)면 그 수에 여유를 얹고, 무한한 키(좌표·정류소·날짜)면 메모리로 감당할 값을 고른다.
     * @param maxWaitForFirstLoad <b>캐시가 빈 키</b>에 동시 요청이 몰렸을 때, 늦은 쪽이 진행 중인 적재를 기다릴 상한.
     *     소유자가 "내 loader 한 번이 얼마나 걸리나" 를 답해야 한다 — 외부 호출 하나짜리면 그 timeout 에 여유를 얹고,
     *     여러 호출을 도는 집계 loader 면 {@link Duration#ZERO} 로 <b>기다리지 않는다</b>(그만큼 기다린 뒤 결국
     *     degrade 하면 즉시 degrade 보다 나쁘다). 이 상한은 <b>이미 값이 있는(stale) 경우엔 쓰이지 않는다</b> —
     *     그때는 지금처럼 즉시 stale 을 준다.
     */
    public ExternalDataCache(int maxEntries, Duration maxWaitForFirstLoad) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 는 1 이상이어야 합니다: " + maxEntries);
        }
        Objects.requireNonNull(maxWaitForFirstLoad, "maxWaitForFirstLoad");
        if (maxWaitForFirstLoad.isNegative()) {
            throw new IllegalArgumentException("maxWaitForFirstLoad 는 음수일 수 없습니다: " + maxWaitForFirstLoad);
        }
        this.maxEntries = maxEntries;
        this.maxWaitForFirstLoad = maxWaitForFirstLoad;
    }

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
        // single-flight: 이 키를 이미 다른 스레드가 갱신 중이라면
        //  · stale 이 있으면 즉시 준다(이 프리미티브의 설계 의도 — 막지 않는다).
        //  · stale 이 없으면(첫 적재) 폴백을 즉시 주면 안 된다. 줄 게 없어서 실패로 답하는 것뿐인데, 그 폴백이
        //    실패를 뜻하는 캐시에서는 사용자가 502 를 받고 빈 값을 뜻하는 캐시에서는 조용히 빈 화면이 된다.
        //    진행 중인 적재를 상한만큼 기다렸다가 그 결과를 나눠 갖는다.
        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            V stale = cached != null ? cached.value() : null;
            if (stale != null) {
                return stalePolicy.degrade(stale, noStaleFallback);
            }
            return awaitFirstLoad(key, running, stalePolicy, noStaleFallback);
        }
        // 이 로드가 "어느 세대에서 시작했는지" 를 적어 둔다. 저장 직전에 비교해, 그사이 무효화됐으면 버린다.
        long startedGeneration = generation.get();
        try {
            // double-check: 게이트 획득 사이 다른 스레드가 이미 갱신했을 수 있다.
            Entry<V> latest = cache.get(key);
            if (latest != null && latest.isFresh()) {
                return complete(mine, latest.value());
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
                return complete(mine, stalePolicy.degrade(stale, noStaleFallback));
            }
            storeIfCurrent(key, new Entry<>(loaded.value(), Instant.now().plus(loaded.ttl())), startedGeneration);
            return complete(mine, loaded.value());
        } finally {
            // 어떤 경로로 빠져나가도 기다리는 쪽을 매달아 두지 않는다. 위에서 이미 완료됐으면 no-op 이다.
            mine.complete(stalePolicy.degrade(null, noStaleFallback));
            inFlight.remove(key, mine);
        }
    }

    /** 적재 결과를 기다리는 쪽에게도 나눠 주고 그대로 돌려준다. */
    private V complete(CompletableFuture<V> mine, V value) {
        mine.complete(value);
        return value;
    }

    /**
     * 빈 키의 첫 적재를 기다린다 — 상한을 넘기면 지금까지처럼 degrade 한다.
     *
     * <p>기다리는 쪽은 <b>적재하는 쪽보다 늦게 끝나지 않는다.</b> 어차피 자기가 적재했어도 같은 시간이 걸렸을
     * 뿐이라, 이 대기는 지연을 새로 만들지 않고 실패를 정상 값으로 바꾼다.
     */
    private V awaitFirstLoad(K key, CompletableFuture<V> running, StalePolicy stalePolicy, V noStaleFallback) {
        if (maxWaitForFirstLoad.isZero()) {
            return stalePolicy.degrade(null, noStaleFallback);
        }
        try {
            return running.get(maxWaitForFirstLoad.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("첫 적재 대기 상한({}) 초과 — 폴백으로 degrade key={}", maxWaitForFirstLoad, key);
        } catch (ExecutionException e) {
            log.warn("첫 적재가 실패로 끝났습니다 — 폴백으로 degrade key={}", key, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("첫 적재 대기가 중단됐습니다 — 폴백으로 degrade key={}", key);
        }
        return stalePolicy.degrade(null, noStaleFallback);
    }

    /**
     * <b>적재하지 않고</b> 신선한 값만 꺼낸다 — 없으면 빈 Optional.
     *
     * <p>요청 경로가 외부를 물지 않아야 하는 값에 쓴다. {@link #get} 은 없으면 loader 를 돌려 사용자를
     * 기다리게 하는데, <b>부가 정보</b>는 그럴 값이 아니다. 없으면 그냥 없는 채로 내리고, 채우는 일은 워밍에
     * 맡긴다.
     *
     * <p>실제로 그 차이가 컸다. 에어코리아를 요청 경로에서 부르던 때 홈이 24초 걸렸고, 실측(2026-08-10,
     * 102회)에서 그 API 는 <b>호출의 36%가 실패하고 성공도 p95 가 5.4초</b>였다. 어떤 timeout 을 골라도
     * 요청 경로에서 부르는 한 사용자가 그 분포를 떠안는다.
     *
     * <p>만료된 값은 주지 않는다. 워밍 주기가 TTL 보다 짧으면 정상 상태에서 늘 신선하고, 워밍이 실패해
     * 만료됐다면 "값이 없다" 가 사실에 가깝다.
     */
    public Optional<V> peek(K key) {
        Entry<V> cached = cache.get(key);
        return cached != null && cached.isFresh() ? Optional.ofNullable(cached.value()) : Optional.empty();
    }

    /**
     * 캐시 무효화 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트 격리용.
     *
     * <p><b>진행 중인 로드까지 무효로 만든다.</b> 저장된 값만 비우면, 이미 시작된 로드가 끝나면서 옛 값을
     * 다시 넣어 무효화가 없던 일이 된다(#215).
     *
     * <ul>
     *   <li>세대를 올린다 — 이전 세대에서 시작된 로드의 결과는 저장되지 않는다.
     *   <li>{@code inFlight} 도 비운다 — 무효화 직후 들어온 요청이 <b>옛 로드에 합류해</b> 그 결과를 받지
     *       않게. 새로 시작하게 두는 편이 맞다.
     * </ul>
     *
     * <p>이미 결과를 기다리고 있던 요청은 그 옛 값을 받는다. 그 요청은 무효화보다 먼저 시작했으므로
     * 계약 위반이 아니다 — 무효화가 약속하는 것은 "이 시점 이후로 옛 값이 남지 않는다" 이다.
     */
    public void evictAll() {
        synchronized (evictLock) {
            generation.incrementAndGet();
            cache.clear();
            inFlight.clear();
        }
    }

    /** 현재 보관 중인 엔트리 수 — 상한이 실제로 지켜지는지 확인하는 용도(테스트·운영 점검). */
    public int size() {
        return cache.size();
    }

    /**
     * 시작할 때와 같은 세대일 때만 저장한다 — 그사이 무효화됐으면 버린다(#215).
     *
     * <p>비교와 저장을 한 덩어리로 묶어야 "확인 직후 무효화되고 그 뒤에 저장" 하는 창이 안 남는다.
     */
    private void storeIfCurrent(K key, Entry<V> entry, long startedGeneration) {
        synchronized (evictLock) {
            if (generation.get() != startedGeneration) {
                // 무효화된 뒤 도착한 옛 결과다. 조용히 버리면 "왜 안 채워졌지" 가 되므로 남긴다.
                log.debug("적재 중 캐시가 무효화되어 결과를 버립니다 key={}", key);
                return;
            }
            store(key, entry);
        }
    }

    /** 새 값을 넣고, 상한을 넘겼으면 축출한다. */
    private void store(K key, Entry<V> entry) {
        cache.put(key, entry);
        if (cache.size() > maxEntries) {
            evictOverflow();
        }
    }

    /**
     * 상한 초과분을 덜어낸다. 만료된 것부터 버리고(값이 죽었는데 자리만 차지한다), 그래도 넘치면 가장 먼저 만료될 것부터 버린다.
     *
     * <p>{@code size()} 는 동시 갱신 중 근사값이고 스냅샷도 정확한 시점이 아니지만, 상한은 <b>메모리가 무한히 자라지 않게</b>
     * 하는 장치라 몇 개 오차는 무해하다. 정확한 상한을 위해 전역 잠금을 걸면 외부 I/O 를 막지 않는다는 이 캐시의 성질이 깨진다.
     */
    private void evictOverflow() {
        // 값을 고정한 스냅샷을 뜬다. ConcurrentHashMap 의 entrySet 뷰는 값이 살아 움직여서, 판단한 값과 지우는 값이
        // 달라질 수 있다.
        List<Map.Entry<K, Entry<V>>> snapshot = new ArrayList<>();
        cache.forEach((key, entry) -> snapshot.add(Map.entry(key, entry)));

        // 삭제는 전부 조건부(remove(key, value))다. 무조건 지우면, 스냅샷 이후 다른 스레드가 같은 키에 넣은
        // <b>방금 갱신된 값</b>까지 날아가 곧바로 외부 재호출을 부른다 — 캐시가 스스로 미스를 만드는 셈이다.
        snapshot.stream()
                .filter(entry -> !entry.getValue().isFresh())
                .forEach(entry -> cache.remove(entry.getKey(), entry.getValue()));

        int overflow = cache.size() - maxEntries;
        if (overflow <= 0) {
            return;
        }
        // 그래도 넘치면 가장 먼저 만료될 것부터. 조건부 remove 는 이미 지워졌거나 그 사이 갱신된 항목에 대해 no-op 이라,
        // limit(overflow) 로 개수를 미리 자르면 그 no-op 이 자리를 차지해 정작 덜 지운다 — <b>실제로 지워진 수</b>를 센다.
        int removed = 0;
        for (Map.Entry<K, Entry<V>> entry : sortedByExpiry(snapshot)) {
            if (removed >= overflow) {
                break;
            }
            if (cache.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        log.debug("캐시 상한({}) 초과 — {}건 축출", maxEntries, removed);
    }

    /** 가장 먼저 만료될 것부터. 만료된 항목도 빼지 않는다 — ①에서 지워졌으면 no-op 이고, 아직 남았으면 먼저 나가야 한다. */
    private List<Map.Entry<K, Entry<V>>> sortedByExpiry(List<Map.Entry<K, Entry<V>>> snapshot) {
        return snapshot.stream()
                .sorted(Comparator.comparing((Map.Entry<K, Entry<V>> entry) -> entry.getValue().expiresAt()))
                .toList();
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
