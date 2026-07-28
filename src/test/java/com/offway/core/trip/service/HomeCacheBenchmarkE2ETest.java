package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.weather.service.AirQualityService;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 홈 API 캐시 전후 응답시간 실측 — 서버사이드 캐싱의 효과를 숫자로 남긴다(포트폴리오/ADR 근거). data.go.kr 실 키가 있을 때만 돈다
 * (실 외부 호출이라 CI 기본 실행 제외). 워밍(HomeCacheWarmer)은 테스트 컨텍스트에서 비활성(@Profile)이라 캐시가 콜드로 시작해
 * 측정을 통제할 수 있다.
 *
 * <ul>
 *   <li><b>캐시 OFF</b> — 매 호출 전 캐시를 비워, 요청마다 외부 팬아웃(랭킹 관광빅데이터 + 콘텐츠 TourAPI×N + 미세먼지)을 탄다.
 *   <li><b>캐시 ON</b> — 한 번 데운 뒤 호출 → 전부 인메모리 캐시에서 응답.
 * </ul>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class HomeCacheBenchmarkE2ETest {

    private static final int RUNS = 20;
    private static final int REMAINING_LEAVE = 13;

    @Autowired
    private HomeService homeService;

    @Autowired
    private RegionRankingService rankingService;

    @Autowired
    private RegionContentProvider contentProvider;

    @Autowired
    private AirQualityService airQualityService;

    @Test
    void 홈_API_캐시_전후_응답시간_측정() {
        long[] cold = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            evictAll(); // 캐시 OFF — 이 호출은 외부를 직접 팬아웃한다
            cold[i] = measureMillis(() -> {
                homeService.home(REMAINING_LEAVE);
                return 0L;
            });
        }

        homeService.home(REMAINING_LEAVE); // 캐시 ON — 첫 호출로 캐시를 데운다

        long[] warm = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            warm[i] = measureMillis(() -> {
                homeService.home(REMAINING_LEAVE);
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
        airQualityService.evictCache();
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
