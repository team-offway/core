package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;

/**
 * 요청 하나가 태운 외부 호출(#421).
 *
 * <p>여기서 잠그는 것은 <b>병렬로 올려도 합계가 맞는가</b> 다. 코스 생성이 가장 많이 태우는 경로가
 * 팬아웃이라, 이게 틀리면 알림이 <b>늘 실제보다 작게</b> 나가고 그건 없는 것보다 나쁘다.
 */
class RequestUsageTest {

    @Test
    void 안_부르면_비어_있다() {
        RequestUsage usage = new RequestUsage();

        assertTrue(usage.isEmpty());
        assertEquals(0, usage.total());
        assertTrue(usage.snapshot().isEmpty());
    }

    @Test
    void 부른_API_만_담는다() {
        RequestUsage usage = new RequestUsage();
        usage.record(ExternalApi.TOUR_API);
        usage.record(ExternalApi.TOUR_API);
        usage.record(ExternalApi.TMAP_ROUTE);

        Map<ExternalApi, Long> snapshot = usage.snapshot();

        assertEquals(2, snapshot.size(), "안 부른 API 까지 담으면 알림 줄이 0 으로 채워진다");
        assertEquals(2L, snapshot.get(ExternalApi.TOUR_API));
        assertEquals(1L, snapshot.get(ExternalApi.TMAP_ROUTE));
        assertEquals(3, usage.total());
        assertFalse(usage.isEmpty());
    }

    /**
     * <b>순서가 매번 같아야 한다.</b> 알림 두 개를 눈으로 비교하려는 값인데 순서가 흔들리면 못 한다.
     *
     * <p>{@code EnumMap} 으로 든 이유가 이것이고, {@code snapshot} 이 {@code Map.copyOf} 를 쓰면
     * 그 순간 무너진다 — 그쪽은 순회 순서를 보장하지 않는다.
     */
    @Test
    void 선언_순서대로_돌려준다() {
        RequestUsage usage = new RequestUsage();
        // 일부러 선언 역순으로 올린다
        usage.record(ExternalApi.KMA_WEATHER);
        usage.record(ExternalApi.TMAP_ROUTE);
        usage.record(ExternalApi.TOUR_API);

        List<ExternalApi> order = List.copyOf(usage.snapshot().keySet());

        assertEquals(List.of(ExternalApi.TOUR_API, ExternalApi.TMAP_ROUTE, ExternalApi.KMA_WEATHER), order);
    }

    /**
     * <b>이 작업의 핵심 단언이다.</b>
     *
     * <p>여러 스레드가 동시에 같은 집계를 올린다 — 코스 생성의 팬아웃이 정확히 그 모양이다.
     * {@code CyclicBarrier} 로 출발을 맞춰 경합을 실제로 만든다.
     */
    @Test
    void 여러_스레드가_동시에_올려도_합계가_맞는다() throws Exception {
        int threads = 8;
        int perThread = 500;
        RequestUsage usage = new RequestUsage();
        CyclicBarrier gate = new CyclicBarrier(threads);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    gate.await();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                for (int n = 0; n < perThread; n++) {
                    usage.record(ExternalApi.TOUR_API);
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals((long) threads * perThread, usage.total());
        assertEquals((long) threads * perThread, usage.snapshot().get(ExternalApi.TOUR_API));
    }
}
