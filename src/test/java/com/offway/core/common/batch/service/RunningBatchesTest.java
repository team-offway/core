package com.offway.core.common.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 같은 배치가 <b>겹쳐 돌지 않는가</b>(#540).
 *
 * <p>이 표식이 막는 것은 "또 돌 때가 됐나"({@code tryStartSince})가 아니라 <b>"지금 도는 중인가"</b> 다.
 * 둘은 독립이라 간격 가드를 통과한 두 실행이 동시에 들어오면 둘 다 통과한다.
 */
class RunningBatchesTest {

    private static final String NAME = "some-batch";

    @Test
    void 도는_중이_아니면_진입한다() {
        RunningBatches batches = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();

        assertTrue(batches.runExclusively(NAME, ran::incrementAndGet));

        assertEquals(1, ran.get());
    }

    /**
     * <b>이 PR 의 핵심.</b> 한쪽이 도는 중에 들어온 쪽은 진입하지 못하고 <b>아무 일도 안 한다</b>.
     *
     * <p>안에서 기다렸다가 두 번째가 확실히 겹치게 만든다 — 순서에 기대면 통과와 실패가 실행마다 갈린다.
     */
    @Test
    void 도는_중이면_두_번째는_아무_일도_안_한다() throws Exception {
        RunningBatches batches = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> batches.runExclusively(NAME, () -> {
            ran.incrementAndGet();
            entered.countDown();
            await(release);
        }));
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "첫 실행이 진입하지 못했다");

        boolean second = batches.runExclusively(NAME, ran::incrementAndGet);

        release.countDown();
        first.join(5_000);

        assertFalse(second, "겹쳐 들어왔는데 진입했다 — 외부 호출이 두 배로 나간다");
        assertEquals(1, ran.get(), "두 번째가 실제로 돌았다 — 교체 중인 데이터를 서로 밟는다");
    }

    /** 끝나면 표식이 풀린다 — 안 풀리면 그 배치가 영영 안 돈다. */
    @Test
    void 끝나면_다시_돌_수_있다() {
        RunningBatches batches = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();

        assertTrue(batches.runExclusively(NAME, ran::incrementAndGet));
        assertTrue(batches.runExclusively(NAME, ran::incrementAndGet));

        assertEquals(2, ran.get());
    }

    /**
     * <b>터져도 표식이 풀린다.</b> 안 풀리면 한 번 실패한 배치가 재배포 전까지 영영 안 돈다 —
     * 조용히 멈추는 가장 나쁜 형태다.
     */
    @Test
    void 안에서_터져도_표식이_풀린다() {
        RunningBatches batches = new RunningBatches();

        assertThrows(IllegalStateException.class, () -> batches.runExclusively(NAME, () -> {
            throw new IllegalStateException("배치가 터졌다");
        }));

        assertTrue(batches.runExclusively(NAME, () -> { }), "표식이 안 풀려 다음 회차가 막혔다");
    }

    /** 예외를 삼키지 않는다 — 손으로 부른 이유가 "무엇이 잘못됐나" 다. */
    @Test
    void 안에서_난_예외는_그대로_올라간다() {
        RunningBatches batches = new RunningBatches();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> batches.runExclusively(NAME, () -> {
                    throw new IllegalStateException("사유");
                }));

        assertEquals("사유", e.getMessage());
    }

    /** 이름이 다르면 서로 안 막는다 — 배치 열다섯이 한 줄로 서면 새벽이 밀린다. */
    @Test
    void 다른_배치는_서로_막지_않는다() throws Exception {
        RunningBatches batches = new RunningBatches();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread first = new Thread(() -> batches.runExclusively(NAME, () -> {
            entered.countDown();
            await(release);
        }));
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        boolean other = batches.runExclusively("another-batch", () -> { });

        release.countDown();
        first.join(5_000);

        assertTrue(other);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
