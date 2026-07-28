package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.weather.service.AirQualityService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 홈·추천이 쓰는 느린 외부 데이터(방문자 랭킹·지역 콘텐츠)를 <b>요청 경로 밖</b>에서 미리 데운다. 89개 지역은 고정이고 데이터가 느리게
 * 변하므로, 부팅 직후와 주기적으로 캐시를 채워 첫 요청부터 즉답이 되게 한다(지연 캐시라면 첫 요청이 6초×N 을 뒤집어쓴다).
 *
 * <p>워밍은 전적으로 best-effort — 외부가 실패해도 랭킹·콘텐츠는 스스로 degrade 하므로 부팅·서비스에 영향이 없다. 실제 앱이 뜨는
 * {@code local}·{@code prod} 에서만 돈다(테스트 컨텍스트에선 캐시를 오염시키지 않게 비활성).
 *
 * <p><b>캐시 위치 — 단일 인스턴스 전제.</b> 랭킹·콘텐츠·대기질 캐시는 각 서비스의 JVM 인메모리(AtomicReference·
 * ConcurrentHashMap)다. 인스턴스를 늘리면 히트율이 나뉘고 워밍·single-flight 가 인스턴스별로 따로 돌아 외부 호출·쿼터
 * 소모가 인스턴스 수만큼 는다. 공모전 규모(단일 인스턴스)에선 로컬 캐시가 더 빠르고 단순해 <b>의식적으로</b> 택했고,
 * 스케일아웃하면 공유 캐시(Redis, 이미 의존성 보유)로 옮긴다. 워밍은 {@code prod} 다중 인스턴스에선 리더 1대만 돌리도록
 * 후속에서 조정한다.
 */
@Slf4j
@Component
@Profile("local | prod")
@RequiredArgsConstructor
public class HomeCacheWarmer {

    /** 부팅 후 첫 워밍까지 지연 — 기동·헬스체크를 방해하지 않게 잠깐 둔다. */
    private static final String INITIAL_DELAY = "PT10S";
    /** 워밍 주기 — 캐시 TTL(6시간)보다 짧게 둬 만료 직전 구간 없이 항상 fresh 로 유지한다(워밍이 만료를 앞질러 갱신). */
    private static final String REFRESH_INTERVAL = "PT5H";
    /** 콘텐츠 워밍 동시성 — 외부가 느려도(지역당 최대 read-timeout) 89개를 순차로 기다리지 않게. 상한을 둬 외부 부하를 억제한다. */
    private static final int WARM_CONCURRENCY = 12;

    private final RegionRepository regionRepository;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final AirQualityService airQualityService;

    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = REFRESH_INTERVAL)
    public void warm() {
        List<Region> regions;
        try {
            regions = regionRepository.findAll();
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 실패 — 지역 조회 실패", e);
            return;
        }
        if (regions.isEmpty()) {
            return;
        }
        // 각 단계를 격리한다 — 한 지역·랭킹의 예외가 나머지 워밍을 통째로 중단시키지 않게.
        try {
            regionRankingService.rankByVisitors(regions); // 방문자 랭킹 캐시(실패해도 폴백)
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 — 랭킹 워밍 실패(계속)", e);
        }
        int warmed = warmContent(regions);
        warmAirQuality(regions);
        log.info("홈 캐시 워밍 완료 regions={}/{}", warmed, regions.size());
    }

    /** 지역 시도별 대기질을 데운다(에어코리아는 시도당 수 초 걸려 요청 경로에서 부르면 홈이 느려진다). 시도 단위라 중복 제거. */
    private void warmAirQuality(List<Region> regions) {
        regions.stream().map(Region::getSido).distinct().forEach(sido -> {
            try {
                airQualityService.byRegionSido(sido);
            } catch (RuntimeException e) {
                log.warn("홈 캐시 워밍 — 대기질 워밍 실패 sido={}(계속)", sido, e);
            }
        });
    }

    /** 지역 콘텐츠를 제한된 동시성으로 데운다. 지역별 예외는 격리하고, 데운 지역 수를 센다. */
    private int warmContent(List<Region> regions) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(WARM_CONCURRENCY, regions.size()));
        try {
            var futures = regions.stream()
                    .map(region -> pool.submit(() -> regionContentProvider.contentFor(region, regions)))
                    .toList();
            int warmed = 0;
            for (Future<?> future : futures) {
                try {
                    future.get();
                    warmed++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return warmed;
                } catch (Exception e) {
                    log.warn("홈 캐시 워밍 — 지역 콘텐츠 워밍 실패(계속)", e);
                }
            }
            return warmed;
        } finally {
            pool.shutdown();
        }
    }
}
