package com.offway.core.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.cache.ExternalDataCache.Loaded;
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
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<String> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(cache.get(KEY, (k, stale) -> {
                        loads.incrementAndGet();
                        sleepQuietly(50); // 조회가 잠깐 걸리는 동안 나머지 스레드가 게이트에 막히게
                        return new Loaded<>("fresh", LONG_TTL);
                    }, "fallback"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        // 게이트를 잡은 한 스레드만 외부를 호출한다. 나머지는 stale 을 즉시 받는다.
        assertEquals(1, loads.get());
        assertEquals(threads, results.size());
        assertTrue(results.contains("stale"), "게이트에 막힌 스레드는 stale 값을 받아야 한다");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
