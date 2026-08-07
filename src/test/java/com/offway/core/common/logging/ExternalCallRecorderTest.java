package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExternalCallRecorderTest {

    @Test
    void 호출이_없으면_비어_있다() {
        assertTrue(new ExternalCallRecorder().isEmpty());
    }

    @Test
    void 한_번_부르면_횟수_표시가_붙지_않는다() {
        ExternalCallRecorder recorder = new ExternalCallRecorder();
        recorder.record("tour", 840);

        assertFalse(recorder.isEmpty());
        assertEquals("tour 840ms", recorder.summary());
    }

    @Test
    void 같은_시스템을_여러_번_부르면_누적시간과_횟수를_낸다() {
        ExternalCallRecorder recorder = new ExternalCallRecorder();
        recorder.record("tour", 300);
        recorder.record("tour", 240);
        recorder.record("tour", 300);

        assertEquals("tour 840ms×3", recorder.summary());
    }

    @Test
    void 여러_시스템은_누적시간_내림차순으로_낸다() {
        ExternalCallRecorder recorder = new ExternalCallRecorder();
        recorder.record("weather-short", 210);
        recorder.record("tour", 840);

        assertEquals("tour 840ms · weather-short 210ms", recorder.summary());
    }

    @Test
    void 여러_스레드가_동시에_적어도_합계가_맞는다() throws Exception {
        ExternalCallRecorder recorder = new ExternalCallRecorder();
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < perThread; j++) {
                    recorder.record("tour", 1);
                }
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals("tour 800ms×800", recorder.summary());
    }
}
