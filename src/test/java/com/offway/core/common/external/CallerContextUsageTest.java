package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;

/**
 * 요청 집계가 스레드를 <b>따라가는가</b>(#421).
 *
 * <p>이슈가 "유일하게 어려운 곳" 이라고 짚은 자리다. 여기가 틀리면 두 가지로 조용히 망가진다 —
 * 팬아웃이 안 세어져 알림이 작아지거나, 스레드가 풀로 돌아간 뒤 다음 요청이 남의 숫자를 물려받는다.
 */
class CallerContextUsageTest {

    private static final Caller BATCH = Caller.of("테스트배치");

    @Test
    void 열지_않으면_집계가_없다() {
        // 배치는 이 알림 대상이 아니라 여는 곳이 없다 — 없는 것이 정상이다.
        assertTrue(CallerContext.usage().isEmpty());
    }

    @Test
    void 이미_열려_있으면_그것을_그대로_준다() {
        try {
            RequestUsage first = CallerContext.beginUsage();

            assertSame(first, CallerContext.beginUsage(), "새로 만들면 바깥에서 세던 숫자가 끊긴다");
        } finally {
            CallerContext.clear();
        }
    }

    /**
     * <b>비우지 않으면 다음 요청이 남의 숫자를 물려받는다.</b> 미상보다 나쁘다 — 틀린 값이 정상처럼
     * 보이기 때문이다.
     */
    @Test
    void 비우면_다음_요청이_물려받지_않는다() {
        RequestUsage first = CallerContext.beginUsage();
        first.record(ExternalApi.TOUR_API);
        CallerContext.clear();

        assertTrue(CallerContext.usage().isEmpty());

        try {
            assertEquals(0, CallerContext.beginUsage().total());
        } finally {
            CallerContext.clear();
        }
    }

    /**
     * <b>이 작업의 핵심 단언이다.</b> {@code wrap} 이 집계를 <b>같은 참조로</b> 넘겨야 팬아웃이 세어진다.
     *
     * <p>값을 복사하면 병렬로 나간 호출이 통째로 빠지고, 코스 생성이 가장 많이 태우는 경로가 정확히
     * 그쪽이라 알림이 늘 실제보다 작아진다.
     */
    @Test
    void 팬아웃이_같은_집계를_올린다() throws Exception {
        int threads = 6;
        RequestUsage usage = CallerContext.beginUsage();
        try {
            CyclicBarrier gate = new CyclicBarrier(threads);
            List<Thread> workers = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                // 감싸는 것은 요청 스레드다 — 실제 팬아웃과 같은 순서다.
                Runnable task = CallerContext.wrap(() -> {
                    CallerContext.usage().orElseThrow().record(ExternalApi.TOUR_API);
                });
                Thread worker = new Thread(() -> {
                    try {
                        gate.await();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    task.run();
                });
                workers.add(worker);
                worker.start();
            }
            for (Thread worker : workers) {
                worker.join();
            }

            assertEquals(threads, usage.total());
        } finally {
            // 단언이 깨져도 비운다 — 안 비우면 뒤 테스트가 같은 스레드의 집계를 물려받는다.
            CallerContext.clear();
        }
    }

    /** 감싼 작업이 끝나면 그 스레드에 집계가 남지 않는다 — 풀 스레드가 다음 일에 물려주면 안 된다. */
    @Test
    void 감싼_작업이_끝나면_그_스레드는_비어_있다() throws Exception {
        CallerContext.beginUsage();
        try {
            Runnable task =
                    CallerContext.wrap(() -> CallerContext.usage().orElseThrow().record(ExternalApi.TOUR_API));

            List<Boolean> leftBehind = new ArrayList<>();
            Thread worker = new Thread(() -> {
                task.run();
                leftBehind.add(CallerContext.usage().isPresent());
            });
            worker.start();
            worker.join();

            assertEquals(List.of(false), leftBehind);
        } finally {
            CallerContext.clear();
        }
    }

    /**
     * <b>주체를 되돌리는 것이 집계를 지우면 안 된다.</b>
     *
     * <p>집계를 연 뒤 주체 없이 {@code call} 을 한 번 지나면, 예전에는 그 {@code finally} 가
     * {@code clear()} 를 불러 <b>열려 있던 집계까지 지웠다</b>. 그 뒤의 외부 호출은 통째로 안 세어지고
     * 알림만 조용히 작아진다 — 예외도 로그도 안 남아 아무도 모른다.
     *
     * <p>지금 {@code CallerContext.run(...)} 호출부는 전부 배치라 이 경로를 안 타지만, 요청 경로에
     * 하나만 생겨도 시작되는 실패다. 그래서 지금 못 박는다.
     */
    @Test
    void 주체를_되돌려도_집계는_살아남는다() {
        RequestUsage usage = CallerContext.beginUsage();
        try {
            // 주체가 심어지지 않은 상태에서 한 번 지난다 — 되돌릴 직전 값이 없는 경우다.
            CallerContext.run(Caller.of("중첩작업"), () -> { });

            assertTrue(CallerContext.usage().isPresent(), "집계가 사라지면 그 뒤 호출이 안 세어진다");
            assertSame(usage, CallerContext.usage().orElseThrow(), "같은 집계여야 숫자가 이어진다");

            CallerContext.usage().orElseThrow().record(ExternalApi.TOUR_API);
            assertEquals(1, usage.total());
        } finally {
            CallerContext.clear();
        }
    }

    /** 주체는 그대로 따라간다 — 집계를 얹으면서 기존 동작이 바뀌지 않았다. */
    @Test
    void 주체도_함께_따라간다() throws Exception {
        List<String> seen = new ArrayList<>();
        CallerContext.run(BATCH, () -> {
            Runnable task = CallerContext.wrap(() -> seen.add(CallerContext.current().name()));
            Thread worker = new Thread(task);
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals(List.of(BATCH.name()), seen);
    }
}
