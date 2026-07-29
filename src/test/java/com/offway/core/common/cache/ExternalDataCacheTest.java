package com.offway.core.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.common.cache.ExternalDataCache.StalePolicy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExternalDataCacheTest {

    private static final String KEY = "k";
    private static final Duration LONG_TTL = Duration.ofSeconds(30);
    private static final Duration SHORT_TTL = Duration.ofMillis(40);

    /** latch 대기 상한 — 정상 구현이면 즉시 풀리고, 안 풀리면 hang 대신 실패로 끝나게 하는 안전망. */
    private static final long AWAIT_SECONDS = 5;

    @Test
    void 값이_신선하면_loader를_다시_부르지_않는다() {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();
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
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();

        cache.get(KEY, (k, stale) -> new Loaded<>("old", SHORT_TTL), "fallback");
        Thread.sleep(60); // TTL 만료
        String after = cache.get(KEY, (k, stale) -> new Loaded<>("new", LONG_TTL), "fallback");

        assertEquals("new", after);
    }

    @Test
    void 조회_실패시_마지막_성공값을_돌려준다_stale_while_error() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();

        cache.get(KEY, (k, stale) -> new Loaded<>("good", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료 후 재조회가 실패하는 상황 — loader 가 stale 을 폴백으로 반환
        String degraded = cache.get(KEY, (k, stale) -> new Loaded<>(stale, SHORT_TTL), "fallback");

        assertEquals("good", degraded); // 마지막 성공값 유지
    }

    @Test
    void loader가_예외를_던져도_요청_경로로_올리지_않고_stale로_degrade한다() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();
        cache.get(KEY, (k, stale) -> new Loaded<>("good", SHORT_TTL), "fallback");
        Thread.sleep(60); // 만료 후 loader 가 계약을 어기고 예외를 던지는 상황

        String result = cache.get(KEY, (k, stale) -> {
            throw new IllegalStateException("loader 계약 위반");
        }, "fallback");

        assertEquals("good", result); // 마지막 성공값(stale)으로 degrade — 예외 전파 안 함
    }

    @Test
    void loader_예외인데_stale도_없으면_폴백을_돌려준다() {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();

        String result = cache.get(KEY, (k, stale) -> {
            throw new IllegalStateException("loader 계약 위반");
        }, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void 만료_순간_동시_요청이_몰려도_외부는_한_번만_호출된다_single_flight() throws InterruptedException {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();
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
        ExternalDataCache<String, String> cache = new ExternalDataCache<>();
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

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
