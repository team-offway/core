package com.offway.core.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 캐시를 끈 상태(#403).
 *
 * <p>여기서 잠그는 것은 <b>"껐다" 의 뜻이 반쪽이 아니라는 것</b>이다. 저장만 건너뛰고 읽기를 남기면
 * 껐는데도 옛 값이 나오고, 읽기만 건너뛰고 저장을 남기면 다시 켰을 때 껐던 동안의 값이 되살아난다.
 */
class ExternalDataCacheBypassTest {

    private static final Duration WAIT = Duration.ofSeconds(1);
    private static final Duration LONG_TTL = Duration.ofHours(6);

    @Test
    void 켜져_있으면_두_번째_조회는_외부를_안_부른다() {
        AtomicInteger loads = new AtomicInteger();
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(10, WAIT, () -> true);

        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");
        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");

        assertEquals(1, loads.get());
    }

    @Test
    void 꺼져_있으면_부를_때마다_외부를_부른다() {
        AtomicInteger loads = new AtomicInteger();
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(10, WAIT, () -> false);

        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");
        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");

        assertEquals(2, loads.get());
        assertEquals(0, cache.size(), "끈 동안에는 저장하지 않는다");
    }

    /**
     * 껐다 켜도 <b>껐던 동안의 값이 되살아나지 않는다.</b>
     *
     * <p>저장을 남겨 뒀다면 다시 켠 순간 그때 값이 튀어나온다. 어드민 입장에서는 "껐다 켰더니 옛
     * 값이 나온다" 가 되어, 스위치가 무엇을 하는지 믿을 수 없게 된다.
     */
    @Test
    void 껐다_켜도_껐던_동안의_값이_남지_않는다() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(10, WAIT, enabled::get);

        cache.get("k", (key, stale) -> new ExternalDataCache.Loaded<>("껐을 때", LONG_TTL), "폴백");
        enabled.set(true);

        assertTrue(cache.peek("k").isEmpty());
        assertEquals("켰을 때",
                cache.get("k", (key, stale) -> new ExternalDataCache.Loaded<>("켰을 때", LONG_TTL), "폴백"));
    }

    /** 끈 상태에서도 <b>실패했을 때의 모양이 같아야</b> 한다 — 스위치가 장애 성격을 바꾸면 안 된다. */
    @Test
    void 꺼진_채로_loader_가_터져도_폴백을_준다() {
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(10, WAIT, () -> false);

        String value = cache.get("k", (key, stale) -> {
            throw new IllegalStateException("loader 가 계약을 어겼다");
        }, "폴백");

        assertEquals("폴백", value);
    }

    /** 공급자를 안 주면 늘 캐시를 쓴다 — 이 기능이 붙기 전의 동작. */
    @Test
    void 공급자를_안_주면_예전처럼_캐시한다() {
        AtomicInteger loads = new AtomicInteger();
        ExternalDataCache<String, String> cache = new ExternalDataCache<>(10, WAIT);

        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");
        cache.get("k", (key, stale) -> load(loads, "값"), "폴백");

        assertEquals(1, loads.get());
    }

    private static ExternalDataCache.Loaded<String> load(AtomicInteger counter, String value) {
        counter.incrementAndGet();
        return new ExternalDataCache.Loaded<>(value, LONG_TTL);
    }
}
