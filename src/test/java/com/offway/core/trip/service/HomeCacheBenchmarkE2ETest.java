package com.offway.core.trip.service;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 홈 API 캐시 전후 응답시간 실측 — 서버사이드 캐싱의 효과를 숫자로 남긴다(포트폴리오/ADR 근거). data.go.kr 실 키가 있을 때만 돈다
 * (실 외부 호출이라 CI 기본 실행 제외).
 *
 * <ul>
 *   <li><b>캐시 OFF</b> — 매 호출 <b>직전</b> 캐시를 비워, 그 호출이 외부 팬아웃(랭킹 관광빅데이터 + 콘텐츠 TourAPI×N)을 타게 한다.
 *   <li><b>캐시 ON</b> — 한 번 데운 뒤 호출 → 전부 인메모리 캐시에서 응답.
 * </ul>
 *
 * <p><b>워머 통제</b>: 기본 활성 프로파일이 {@code local}({@code application.properties})이라 이 컨텍스트엔 {@code
 * HomeCacheWarmer}(프로파일 {@code local | prod})가 뜬다. 프로파일·프로퍼티 고정 어노테이션으로 끄는 게 이상적이나, 그것들은
 * 컨텍스트 캐시를 깨 프로젝트 훅이 막는다. 대신 <b>측정 직전 evict</b> 가 통제 수단이다 — 워머가 백그라운드로 채워도 각 콜드 호출
 * 직전에 비우므로 콜드는 외부를 탄다. 설령 워머가 한 호출 중 끼어들어 채우면 그 호출은 더 <b>빨라질</b> 뿐이라 OFF 수치를 부풀리지
 * 않는다(=보수적). 실제로 콜드 median 이 10s 대라 워머 간섭은 결론에 무의미하다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class HomeCacheBenchmarkE2ETest {

    private static final int RUNS = 20;
    /** 연차를 저장한 적 없는 소유자 — 이 측정은 외부 팬아웃 시간을 보는 것이라 연차 값은 무관하다. */
    private static final UUID BENCHMARK_USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired
    private HomeService homeService;

    @Autowired
    private RegionRankingService rankingService;

    @Autowired
    private RegionContentProvider contentProvider;

    @Test
    void 홈_API_캐시_전후_응답시간_측정() {
        long[] cold = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            evictAll(); // 캐시 OFF — 이 호출은 외부를 직접 팬아웃한다
            cold[i] = measureMillis(() -> {
                homeService.home(BENCHMARK_USER);
                return 0L;
            });
        }

        homeService.home(BENCHMARK_USER); // 캐시 ON — 첫 호출로 캐시를 데운다

        long[] warm = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            warm[i] = measureMillis(() -> {
                homeService.home(BENCHMARK_USER);
                return 0L;
            });
        }

        long coldMedian = median(cold);
        long warmMedian = median(warm);
        System.out.println("\n================ 홈 API 캐시 전후 응답시간 (실 외부 호출) ================");
        report("캐시 OFF (매 호출 외부 조회)", cold);
        report("캐시 ON  (워밍 후 캐시 응답)", warm);
        System.out.printf(
                "→ median %,dms → %,dms  (약 %,d배 빠름, %.1f%% 단축)%n",
                coldMedian, warmMedian, coldMedian / Math.max(1, warmMedian),
                (1 - (double) warmMedian / Math.max(1, coldMedian)) * 100);
        System.out.println("======================================================================\n");

        assertTrue(warmMedian < coldMedian, "캐시 ON 이 OFF 보다 빨라야 한다");
    }

    private void evictAll() {
        rankingService.evictCache();
        contentProvider.evictCache();
    }

    private static long measureMillis(LongSupplier work) {
        long start = System.nanoTime();
        work.getAsLong();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static void report(String label, long[] ms) {
        long[] sorted = ms.clone();
        Arrays.sort(sorted);
        System.out.printf(
                "  %-24s min %,5dms · median %,5dms · max %,5dms%n",
                label, sorted[0], sorted[sorted.length / 2], sorted[sorted.length - 1]);
    }
}
