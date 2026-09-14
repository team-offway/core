package com.offway.core.common.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.batch.domain.BatchErrorCode;
import com.offway.core.common.batch.domain.BatchException;
import com.offway.core.common.batch.domain.ManualBatch;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 수동 실행과 스케줄 실행이 <b>같은 표식</b>을 잡는가(#540).
 *
 * <p>예전에는 표식이 이 서비스 안에만 있어 <b>수동끼리만</b> 막혔다. 스케줄러는 각 배치의
 * {@code @Scheduled} 메서드를 직접 부르므로 그 표식을 안 지났고, 둘이 함께 돌았다.
 *
 * <p>그래서 여기서 보는 것은 "수동 두 번" 이 아니라 <b>"스케줄이 도는 중에 수동을 누르면"</b> 이다.
 */
class ManualBatchServiceTest {

    private static final String NAME = "some-batch";

    /**
     * 배치 흉내 — 진짜 배치처럼 <b>스케줄 진입점과 수동 진입점이 같은 표식을 지난다</b>.
     *
     * <p>이 구조가 곧 이 PR 이 열다섯 곳에 심은 모양이다.
     */
    private static final class FakeBatch implements ManualBatch {

        private final RunningBatches runningBatches;
        private final Runnable body;

        private FakeBatch(RunningBatches runningBatches, Runnable body) {
            this.runningBatches = runningBatches;
            this.body = body;
        }

        @Override
        public String batchName() {
            return NAME;
        }

        @Override
        public boolean runNow() {
            return runningBatches.runExclusively(NAME, body);
        }

        /** 스케줄러가 부르는 자리. */
        void scheduled() {
            runNow();
        }
    }

    @Test
    void 도는_중이_아니면_돌린다() {
        RunningBatches running = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();
        ManualBatchService service = new ManualBatchService(
                List.of(new FakeBatch(running, ran::incrementAndGet)));

        service.run(NAME);

        assertEquals(1, ran.get());
    }

    /**
     * <b>이 PR 의 핵심.</b> 스케줄러가 도는 중에 손으로 누르면 409 다 — 함께 돌지 않는다.
     */
    @Test
    void 스케줄이_도는_중에_손으로_누르면_409() throws Exception {
        RunningBatches running = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        FakeBatch batch = new FakeBatch(running, () -> {
            ran.incrementAndGet();
            entered.countDown();
            await(release);
        });
        ManualBatchService service = new ManualBatchService(List.of(batch));

        Thread scheduler = new Thread(batch::scheduled);
        scheduler.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "스케줄 실행이 진입하지 못했다");

        BatchException e = assertThrows(BatchException.class, () -> service.run(NAME));

        release.countDown();
        scheduler.join(5_000);

        assertEquals(BatchErrorCode.ALREADY_RUNNING.code(), e.errorCode().code());
        assertEquals(1, ran.get(), "겹쳐 돌았다 — 외부 호출이 두 배로 나가고 데이터를 서로 밟는다");
    }

    /**
     * 반대 방향도 같다 — 손으로 도는 중에 스케줄러가 깨면 <b>건너뛴다</b>. 그쪽은 409 가 아니라
     * 로그 한 줄이다(부를 사람이 없다).
     */
    @Test
    void 손으로_도는_중에_스케줄이_깨면_건너뛴다() throws Exception {
        RunningBatches running = new RunningBatches();
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        FakeBatch batch = new FakeBatch(running, () -> {
            ran.incrementAndGet();
            entered.countDown();
            await(release);
        });
        ManualBatchService service = new ManualBatchService(List.of(batch));

        Thread manual = new Thread(() -> service.run(NAME));
        manual.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        batch.scheduled(); // 스케줄러가 깼다 — 던지지 않고 그냥 건너뛴다

        release.countDown();
        manual.join(5_000);

        assertEquals(1, ran.get());
    }

    /** 없는 이름은 404 다 — 겹침(409)과 뜻이 다르다. */
    @Test
    void 없는_이름은_따로_가른다() {
        ManualBatchService service = new ManualBatchService(
                List.of(new FakeBatch(new RunningBatches(), () -> { })));

        BatchException e = assertThrows(BatchException.class, () -> service.run("없는배치"));

        assertEquals(BatchErrorCode.UNKNOWN_BATCH.code(), e.errorCode().code());
    }

    /**
     * <b>배치가 터진 것과 겹친 것은 다른 사건이다.</b> 겹침을 일반 실패로 덮으면 409 가 500 으로 나가,
     * 관리자가 "서버가 고장났다" 로 읽는다.
     */
    @Test
    void 배치가_터지면_겹침이_아니라_실패다() {
        ManualBatchService service = new ManualBatchService(
                List.of(new FakeBatch(new RunningBatches(), () -> {
                    throw new IllegalStateException("배치가 터졌다");
                })));

        BatchException e = assertThrows(BatchException.class, () -> service.run(NAME));

        assertEquals(BatchErrorCode.RUN_FAILED.code(), e.errorCode().code());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
