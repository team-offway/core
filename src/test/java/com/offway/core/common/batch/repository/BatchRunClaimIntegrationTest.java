package com.offway.core.common.batch.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 그날 실행을 <b>한 번만</b> 선점하는가(#314).
 *
 * <p>{@code RegionPoiRefreshService} 는 cron 과 부팅 확인 두 트리거를 쓰고 스케줄러 풀이 2 라, 둘이 동시에
 * 깨어날 수 있다. "확인 → 267콜 → 기록" 이던 예전 구조에서는 둘 다 "아직 안 돌았다" 를 읽어 같은 날 콜을
 * <b>두 번</b> 쐈다 — 관광정보 일일 한도(1,000)의 절반이 넘는다.
 *
 * <p>트랜잭션을 걸지 않는다. 클래스 레벨 {@code @Transactional} 로 감싸면 두 스레드가 서로의 커밋을 못 봐서
 * <b>검증하려는 경합 자체가 사라진다</b>. 대신 배치 이름을 테스트마다 다르게 둬 서로 간섭하지 않게 한다.
 */
@SpringBootTest
class BatchRunClaimIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @Autowired
    private BatchRunRepository batchRunRepository;

    @Test
    void 처음이면_선점한다() {
        String name = "claim-first-" + System.nanoTime();

        assertTrue(batchRunRepository.tryStartOn(name, TODAY, TODAY.atTime(4, 0)));
    }

    @Test
    void 같은_날_두_번째는_진다() {
        String name = "claim-twice-" + System.nanoTime();
        batchRunRepository.tryStartOn(name, TODAY, TODAY.atTime(4, 0));

        assertFalse(batchRunRepository.tryStartOn(name, TODAY, TODAY.atTime(4, 2)));
    }

    /** 하루가 지나면 다시 선점된다 — 아니면 배치가 영영 멈춘다. */
    @Test
    void 다음_날은_다시_선점한다() {
        String name = "claim-nextday-" + System.nanoTime();
        batchRunRepository.tryStartOn(name, TODAY, TODAY.atTime(4, 0));

        assertTrue(batchRunRepository.tryStartOn(name, TODAY.plusDays(1), TODAY.plusDays(1).atTime(4, 0)));
    }

    /** 선점은 곧 기록이다 — 뒤따르는 작업이 실패해도 같은 날 다시 쏘지 않아야 한다. */
    @Test
    void 선점하면_실행_기록이_남는다() {
        String name = "claim-marks-" + System.nanoTime();

        batchRunRepository.tryStartOn(name, TODAY, TODAY.atTime(4, 0));

        assertTrue(batchRunRepository.hasRunOn(name, TODAY));
    }

    /**
     * <b>이 테스트가 이 변경의 이유다.</b> 두 트리거가 같은 순간에 깨어나도 한쪽만 이겨야 한다.
     *
     * <p>진 쪽이 그냥 도는 구조였다면 여기서 2 가 나온다 — 그게 267콜을 두 번 쏘던 상태다.
     */
    @Test
    void 동시에_깨어나도_한_쪽만_이긴다() throws Exception {
        String name = "claim-race-" + System.nanoTime();
        int racers = 2;
        CyclicBarrier startTogether = new CyclicBarrier(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<Boolean>> attempts = List.of(
                    claim(name, startTogether), claim(name, startTogether));

            long won = 0;
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                won += Boolean.TRUE.equals(result.get()) ? 1 : 0;
            }

            assertEquals(1, won, "동시에 둘이 선점하면 같은 날 외부 호출이 두 배가 된다");
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> claim(String name, CyclicBarrier startTogether) {
        return () -> {
            startTogether.await();
            return batchRunRepository.tryStartOn(name, TODAY, LocalDateTime.of(TODAY, java.time.LocalTime.of(4, 0)));
        };
    }
}
