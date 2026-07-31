package com.offway.core.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.cache.ExternalDataCache.StalePolicy;
import java.time.Duration;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExternalDataCacheTest {

    private static final String KEY = "k";
    private static final Duration LONG_TTL = Duration.ofSeconds(30);
    private static final Duration SHORT_TTL = Duration.ofMillis(40);

    /** latch 대기 상한 — 정상 구현이면 즉시 풀리고, 안 풀리면 hang 대신 실패로 끝나게 하는 안전망. */
    private static final long AWAIT_SECONDS = 5;

    /** 상한을 검증하지 않는 테스트용 — 그 테스트들이 쓰는 키 수보다 충분히 커서 축출이 끼어들지 않는다. */
    private static final int TEST_MAX_ENTRIES = 100;

    /** 기존 시나리오는 "적재가 곧 끝난다" 를 전제하므로 넉넉히 준다. 대기 자체를 보는 테스트는 각자 값을 정한다. */
    private static final Duration TEST_FIRST_LOAD_WAIT = Duration.ofSeconds(5);

    @Test
    void 값이_신선하면_loader를_다시_부르지_않는다() {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get(KEY, (k, stale) -> {
            loads.incrementAndGet();
            return new Loaded<>("v1", LONG_TTL);
        }, "fallback");
        String second = cache.get(KEY, (k, stale) -> {
            loads.incrementAndGet();
            return new Loaded<>("v2", LONG_TTL);
        }, "fallback");

        assertEquals("v1", first);
        assertEquals("v1", second); // 캐시 히트 — 두 번째 loader 미실행
        assertEquals(1, loads.get());
    }

    @Test
    void 만료되면_다시_조회한다() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);

        cache.get(KEY, (k, stale) -> new Loaded<>("old", SHORT_TTL), "fallback");
        Thread.sleep(60); // TTL 만료
        String after = cache.get(KEY, (k, stale) -> new Loaded<>("new", LONG_TTL), "fallback");

        assertEquals("new", after);
    }

    @Test
    void 조회_실패시_마지막_성공값을_돌려준다_stale_while_error() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);

        cache.get(KEY, (k, stale) -> new Loaded<>("good", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료 후 재조회가 실패하는 상황 — loader 가 stale 을 폴백으로 반환
        String degraded = cache.get(KEY, (k, stale) -> new Loaded<>(stale, SHORT_TTL), "fallback");

        assertEquals("good", degraded); // 마지막 성공값 유지
    }

    @Test
    void loader가_예외를_던져도_요청_경로로_올리지_않고_stale로_degrade한다() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("good", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료 후 loader 가 계약을 어기고 예외를 던지는 상황

        String result = cache.get(KEY, (k, stale) -> {
            throw new IllegalStateException("loader 계약 위반");
        }, "fallback");

        assertEquals("good", result); // 마지막 성공값(stale)으로 degrade — 예외 전파 안 함
    }

    @Test
    void loader_예외인데_stale도_없으면_폴백을_돌려준다() {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);

        String result = cache.get(KEY, (k, stale) -> {
            throw new IllegalStateException("loader 계약 위반");
        }, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void stale_미제공이면_loader_예외로_degrade할_때도_stale대신_폴백을_돌려준다() throws InterruptedException {
        // 위 loader가_예외를_던져도... 의 DISALLOW_STALE 짝 — degrade 두 경로가 같은 정책을 따르는지 확인한다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("stale", SHORT_TTL), "fallback", StalePolicy.DISALLOW_STALE);
        Thread.sleep(60); // 만료 — stale 이 남은 상태에서 loader 가 계약을 어기고 예외를 던진다

        String result = cache.get(KEY, (k, stale) -> {
            throw new IllegalStateException("loader 계약 위반");
        }, "fallback", StalePolicy.DISALLOW_STALE);

        assertEquals("fallback", result); // ALLOW_STALE 이면 "stale" 이지만, 실시간 값은 조회 불가가 낫다
    }

    @Test
    void stale_미제공이면_loader가_stale을_새_값으로_되돌려줄_수_없다() throws InterruptedException {
        // 정책이 degrade 만 막으면 loader 반환값이라는 우회로가 남는다. stale 을 아예 안 넘겨 그 통로를 닫는다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("stale", SHORT_TTL), "fallback", StalePolicy.DISALLOW_STALE);
        Thread.sleep(60); // 만료

        AtomicReference<String> seenByLoader = new AtomicReference<>("아직 호출 안 됨");
        String result = cache.get(KEY, (k, stale) -> {
            seenByLoader.set(stale);
            // ALLOW_STALE 호출부의 일반적인 형태 — 조회 실패면 stale 을 그대로 재사용한다.
            return new Loaded<>(stale != null ? stale : "fresh", LONG_TTL);
        }, "fallback", StalePolicy.DISALLOW_STALE);

        assertNull(seenByLoader.get(), "DISALLOW_STALE 이면 loader 에 stale 이 넘어가면 안 된다");
        assertEquals("fresh", result);
    }

    @Test
    void 만료_순간_동시_요청이_몰려도_외부는_한_번만_호출된다_single_flight() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("stale", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료

        int threads = 16;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        // 경합을 sleep 이 아니라 latch 로 고정한다 — 갱신 스레드는 나머지가 전부 stale 을 받은 뒤에야 풀려난다.
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch staleServed = new CountDownLatch(threads - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String result = cache.get(KEY, (k, stale) -> {
                        loads.incrementAndGet();
                        refreshStarted.countDown();
                        awaitQuietly(releaseRefresh); // 나머지 스레드가 게이트에 막힐 때까지 잡고 있는다
                        return new Loaded<>("fresh", LONG_TTL);
                    }, "fallback");
                    results.add(result);
                    if ("stale".equals(result)) {
                        staleServed.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            start.countDown();
            assertTrue(refreshStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS), "갱신 스레드가 loader 에 진입해야 한다");
            // 게이트를 잡은 한 스레드만 외부를 호출한다. 나머지는 stale 을 즉시 받는다.
            assertTrue(staleServed.await(AWAIT_SECONDS, TimeUnit.SECONDS), "게이트에 막힌 스레드는 stale 값을 받아야 한다");
        } finally {
            releaseRefresh.countDown(); // 단언이 깨져도 워커를 반드시 풀어준다
            pool.shutdown();
        }
        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS), "모든 요청이 끝나야 한다");

        assertEquals(1, loads.get());
        assertEquals(threads, results.size());
        assertTrue(results.contains("stale"), "게이트에 막힌 스레드는 stale 값을 받아야 한다");
    }

    @Test
    void stale_미제공이면_갱신_중_동시요청은_stale대신_폴백을_받는다() throws InterruptedException {
        // 실시간 값(버스 도착)용 — DISALLOW_STALE. 만료된 stale 을 절대 내리지 않는다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("stale", SHORT_TTL), "fallback", StalePolicy.DISALLOW_STALE);
        Thread.sleep(60); // 만료 — 이제 캐시엔 만료된 "stale" 이 남아 있다

        int threads = 16;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        // 경합을 sleep 이 아니라 latch 로 고정한다 — 갱신 스레드는 나머지가 전부 폴백을 받은 뒤에야 풀려난다.
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        CountDownLatch fallbackServed = new CountDownLatch(threads - 1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String result = cache.get(KEY, (k, stale) -> {
                        loads.incrementAndGet();
                        refreshStarted.countDown();
                        awaitQuietly(releaseRefresh); // 나머지 스레드가 게이트에 막힐 때까지 잡고 있는다
                        return new Loaded<>("fresh", LONG_TTL);
                    }, "fallback", StalePolicy.DISALLOW_STALE);
                    results.add(result);
                    if ("fallback".equals(result)) {
                        fallbackServed.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        try {
            start.countDown();
            assertTrue(refreshStarted.await(AWAIT_SECONDS, TimeUnit.SECONDS), "갱신 스레드가 loader 에 진입해야 한다");
            // 핵심: 갱신 중 막힌 스레드는 만료된 stale 이 아니라 폴백을 받아야 한다(실시간 값이 오래된 채로 노출되지 않게).
            assertTrue(fallbackServed.await(AWAIT_SECONDS, TimeUnit.SECONDS), "게이트에 막힌 스레드는 폴백을 받아야 한다");
        } finally {
            releaseRefresh.countDown(); // 단언이 깨져도 워커를 반드시 풀어준다
            pool.shutdown();
        }
        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS), "모든 요청이 끝나야 한다");

        assertEquals(1, loads.get());
        assertEquals(threads, results.size());
        assertTrue(results.stream().noneMatch("stale"::equals), "stale 미제공 모드에선 stale 이 절대 노출되면 안 된다");
    }

    // ── 엔트리 수 상한 (#100) ──────────────────────────────────────
    // TTL 은 값의 신선도만 관리하고 엔트리를 지우지 않는다. 키 공간이 무한하면(좌표·정류소·날짜) 상한이 유일한 방어선이다.

    @Test
    void 상한을_넘기면_축출해_엔트리_수가_상한_안에_머문다() {
        ExternalDataCache<Integer, String> cache = new ExternalDataCache<>(10, TEST_FIRST_LOAD_WAIT);

        for (int i = 0; i < 100; i++) {
            cache.get(i, (k, stale) -> new Loaded<>("v" + k, LONG_TTL), "fallback");
        }

        assertTrue(cache.size() <= 10, "상한을 넘게 쌓이면 안 된다. 실제=" + cache.size());
    }

    @Test
    void 상한을_넘기면_만료된_엔트리부터_버리고_신선한_것은_남긴다() throws InterruptedException {
        ExternalDataCache<Integer, String> cache = new ExternalDataCache<>(3, TEST_FIRST_LOAD_WAIT);

        // 짧은 TTL 두 건을 먼저 넣고 만료시킨다 — 값은 죽었지만 엔트리는 남아 자리를 차지한다.
        cache.get(1, (k, stale) -> new Loaded<>("expired1", SHORT_TTL), "fallback");
        cache.get(2, (k, stale) -> new Loaded<>("expired2", SHORT_TTL), "fallback");
        Thread.sleep(SHORT_TTL.toMillis() * 2);

        // 신선한 값을 상한까지 채운다 — 축출이 일어나면 만료된 1·2 가 먼저 나가야 한다.
        cache.get(3, (k, stale) -> new Loaded<>("fresh3", LONG_TTL), "fallback");
        cache.get(4, (k, stale) -> new Loaded<>("fresh4", LONG_TTL), "fallback");
        cache.get(5, (k, stale) -> new Loaded<>("fresh5", LONG_TTL), "fallback");

        AtomicInteger reloads = new AtomicInteger();
        for (int key : new int[] {3, 4, 5}) {
            cache.get(key, (k, stale) -> {
                reloads.incrementAndGet();
                return new Loaded<>("reloaded", LONG_TTL);
            }, "fallback");
        }

        assertEquals(0, reloads.get(), "신선한 엔트리는 만료된 것보다 먼저 축출되면 안 된다");
    }

    @Test
    void 상한이_0_이하면_생성을_거부한다() {
        // 상한이 없다는 뜻이 되면 이 클래스의 방어가 무력해진다 — 실수로 0 을 넘기는 것을 막는다.
        assertThrows(IllegalArgumentException.class, () -> new ExternalDataCache<String, String>(0, TEST_FIRST_LOAD_WAIT));
        assertThrows(IllegalArgumentException.class, () -> new ExternalDataCache<String, String>(-1, TEST_FIRST_LOAD_WAIT));
    }

    @Test
    void 대기_상한이_음수면_생성을_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExternalDataCache<String, String>(TEST_MAX_ENTRIES, Duration.ofSeconds(-1)));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void 빈_캐시에_동시_요청이_와도_둘_다_정상값을_받는다() throws Exception {
        // 이 프리미티브가 있기 전의 결함 — 늦은 쪽에게 줄 stale 이 없으면 폴백(=실패)을 즉시 줬다.
        // 공휴일 캐시에서는 그게 502 가 됐고, 폴백이 빈 값인 캐시에서는 조용히 빈 화면이 됐다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();

        BiFunction<String, String, Loaded<String>> blockingLoader = (key, stale) -> {
            loads.incrementAndGet();
            loaderEntered.countDown();
            awaitQuietly(releaseLoader);
            return new Loaded<>("fresh", LONG_TTL);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> leader = pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            // 경합 시점을 sleep 으로 추측하지 않는다 — 리더가 loader 에 들어가야 뒤따르는 쪽이 게이트에 막힌다.
            assertTrue(loaderEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "리더가 loader 에 진입해야 한다");
            Future<String> follower = pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));

            releaseLoader.countDown();
            assertEquals("fresh", leader.get(AWAIT_SECONDS, TimeUnit.SECONDS));
            assertEquals("fresh", follower.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            releaseLoader.countDown(); // 단언이 깨져도 워커를 반드시 풀어준다
            pool.shutdownNow();
        }
        assertEquals(1, loads.get(), "외부는 한 번만 불러야 한다 — 기다림이 스탬피드를 만들면 안 된다");
    }
    @Test
    void 첫_적재가_상한을_넘기면_기존처럼_폴백을_준다() throws Exception {
        // 무한정 매달리지 않는다. 요청 경로가 적재만큼 오래 붙잡히면 기다림이 오히려 해가 된다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, Duration.ofMillis(80));
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);

        BiFunction<String, String, Loaded<String>> blockingLoader = (key, stale) -> {
            loaderEntered.countDown();
            awaitQuietly(releaseLoader);
            return new Loaded<>("fresh", LONG_TTL);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertTrue(loaderEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "리더가 loader 에 진입해야 한다");

            // loader 가 아직 잡혀 있으므로 "fresh" 가 나오면 상한을 무시하고 끝까지 기다렸다는 뜻이다.
            Future<String> late = pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertEquals("fallback", late.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            releaseLoader.countDown();
            pool.shutdownNow();
        }
    }
    @Test
    void 상한이_0이면_기다리지_않고_바로_폴백을_준다() throws Exception {
        // 집계 loader 처럼 기다려도 어차피 degrade 할 캐시의 선택지(지역 랭킹).
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, Duration.ZERO);
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);

        BiFunction<String, String, Loaded<String>> blockingLoader = (key, stale) -> {
            loaderEntered.countDown();
            awaitQuietly(releaseLoader);
            return new Loaded<>("fresh", LONG_TTL);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertTrue(loaderEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "리더가 loader 에 진입해야 한다");

            // 경과 시간을 재지 않는다 — loader 가 잡혀 있는 동안 "fallback" 이 나온 것 자체가 안 기다렸다는 증거다.
            Future<String> late = pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertEquals("fallback", late.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            releaseLoader.countDown();
            pool.shutdownNow();
        }
    }
    @Test
    void 이미_stale_이_있으면_기다리지_않고_즉시_stale_을_준다() throws Exception {
        // 이 프리미티브의 설계 의도 — 막지 않는다. 대기는 줄 게 없을 때만이다.
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(TEST_MAX_ENTRIES, TEST_FIRST_LOAD_WAIT);
        cache.get(KEY, (k, stale) -> new Loaded<>("stale", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료 — 이건 경합이 아니라 TTL 이라 시간으로만 표현된다

        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        BiFunction<String, String, Loaded<String>> blockingLoader = (key, ignored) -> {
            loaderEntered.countDown();
            awaitQuietly(releaseLoader);
            return new Loaded<>("fresh", LONG_TTL);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertTrue(loaderEntered.await(AWAIT_SECONDS, TimeUnit.SECONDS), "갱신 스레드가 loader 에 진입해야 한다");

            // loader 가 잡혀 있는데 "stale" 이 나왔다면 기다리지 않고 즉시 받은 것이다.
            Future<String> late = pool.submit(() -> cache.get(KEY, blockingLoader, "fallback"));
            assertEquals("stale", late.get(AWAIT_SECONDS, TimeUnit.SECONDS));
        } finally {
            releaseLoader.countDown();
            pool.shutdownNow();
        }
    }
}
