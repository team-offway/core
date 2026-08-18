package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 호출 주체를 담는 스레드 지역 홀더(#285).
 *
 * <p>여기가 틀리면 내역이 조용히 엉뚱한 주체에 붙거나 통째로 미상이 된다 — 둘 다 알림을 못 믿게 만든다.
 */
class CallerContextTest {

    @Test
    void 심은_적이_없으면_미상이다() {
        assertEquals(Caller.UNKNOWN, CallerContext.current());
    }

    @Test
    void 심으면_그_안에서_보인다() {
        Caller batch = Caller.of("중심관광지배치");

        CallerContext.run(batch, () -> assertEquals(batch, CallerContext.current()));
    }

    /** 스레드가 풀로 돌아가므로 반드시 비워야 한다 — 안 비우면 다음 작업이 남의 주체를 물려받는다. */
    @Test
    void 끝나면_원래대로_돌아온다() {
        CallerContext.run(Caller.of("중심관광지배치"), () -> {
        });

        assertEquals(Caller.UNKNOWN, CallerContext.current());
    }

    @Test
    void 예외로_끝나도_되돌린다() {
        try {
            CallerContext.run(Caller.of("중심관광지배치"), () -> {
                throw new IllegalStateException("의도된 실패");
            });
        } catch (IllegalStateException ignored) {
            // 되돌리는지가 관심사다
        }

        assertEquals(Caller.UNKNOWN, CallerContext.current());
    }

    /** 중첩은 실제로 생긴다 — 배치가 심은 뒤 안쪽에서 다른 주체를 심어도 바깥이 살아남아야 한다. */
    @Test
    void 중첩하면_바깥_주체가_살아남는다() {
        Caller outer = Caller.of("지역콘텐츠배치");
        Caller inner = Caller.of("장소상세");

        CallerContext.run(outer, () -> {
            CallerContext.run(inner, () -> assertEquals(inner, CallerContext.current()));
            assertEquals(outer, CallerContext.current());
        });
    }

    /**
     * <b>이 작업의 핵심 함정이다.</b> {@code RegionContentProvider} 가 팬아웃 풀로 던지는 순간 스레드가
     * 바뀌어 맥락이 사라진다. 감싸지 않으면 지역콘텐츠 배치 130 콜이 통째로 미상이 된다.
     */
    @Test
    void 감싸면_다른_스레드에서도_주체가_따라간다() throws Exception {
        Caller batch = Caller.of("지역콘텐츠배치");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Caller> seen = new AtomicReference<>();
        try {
            Runnable wrapped = CallerContext.call(batch,
                    () -> CallerContext.wrap(() -> seen.set(CallerContext.current())));

            CompletableFuture.runAsync(wrapped, pool).get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(batch, seen.get());
    }

    /** 감싸지 않으면 미상이 된다 — 조용히 남의 주체에 붙는 것보다 이 편이 낫다. */
    @Test
    void 안_감싸면_다른_스레드에서는_미상이다() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Caller> seen = new AtomicReference<>();
        try {
            CallerContext.run(Caller.of("지역콘텐츠배치"), () -> {
                try {
                    CompletableFuture.runAsync(() -> seen.set(CallerContext.current()), pool).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } finally {
            pool.shutdownNow();
        }

        assertEquals(Caller.UNKNOWN, seen.get());
    }

    /** 감싼 시점의 주체를 잡는다 — 풀에 들어간 뒤 제출한 쪽은 이미 다른 일을 하고 있을 수 있다. */
    @Test
    void 감싼_시점의_주체를_잡는다() {
        Caller atWrap = Caller.of("코스생성");
        AtomicReference<Caller> seen = new AtomicReference<>();

        Runnable wrapped = CallerContext.call(atWrap,
                () -> CallerContext.wrap(() -> seen.set(CallerContext.current())));
        CallerContext.run(Caller.of("장소상세"), wrapped);

        assertEquals(atWrap, seen.get());
    }
}
