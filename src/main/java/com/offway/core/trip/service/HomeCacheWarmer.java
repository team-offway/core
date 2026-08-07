package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.weather.service.AirQualityService;
import com.offway.core.weather.service.TourClimateService;
import com.offway.core.weather.service.WeatherService;
import java.util.List;
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
 * <p>캐시 TTL 이 서로 달라 워밍도 두 주기로 나눈다 — 랭킹·콘텐츠(6h TTL)는 5h 주기, <b>대기질(1h TTL)은 50분 주기</b>로
 * 각자의 만료를 앞질러 항상 fresh 로 유지한다(단일 주기면 대기질이 워밍 사이에 만료돼 요청자가 에어코리아 실시간 호출을 떠안는다).
 *
 * <p><b>캐시 위치 — 단일 인스턴스 전제.</b> 랭킹·콘텐츠·대기질 캐시는 각 서비스의 JVM 인메모리({@link
 * com.offway.core.common.cache.ExternalDataCache})다. 인스턴스를 늘리면 히트율이 나뉘고 워밍·single-flight 가 인스턴스별로
 * 따로 돌아 외부 호출·쿼터 소모가 인스턴스 수만큼 는다. 공모전 규모(단일 인스턴스)에선 로컬 캐시가 더 빠르고 단순해 <b>의식적으로</b>
 * 택했고, 스케일아웃하면 공유 캐시(Redis, 이미 의존성 보유)로 옮긴다. 워밍은 {@code prod} 다중 인스턴스에선 리더 1대만 돌리도록
 * 후속에서 조정한다.
 */
@Slf4j
@Component
@Profile("local | prod")
@RequiredArgsConstructor
public class HomeCacheWarmer {

    /** 부팅 후 첫 워밍까지 지연 — 기동·헬스체크를 방해하지 않게 잠깐 둔다. */
    private static final String INITIAL_DELAY = "PT10S";
    /** 랭킹·콘텐츠 워밍 주기 — 그 캐시 TTL(6시간)보다 짧게 둬 만료 직전 구간 없이 항상 fresh(워밍이 만료를 앞질러 갱신). */
    private static final String SLOW_REFRESH_INTERVAL = "PT5H";
    /** 대기질 워밍 주기 — 대기질 캐시 TTL(1시간)보다 짧게. 시간당 갱신 데이터라 짧은 주기로 따로 데운다. */
    private static final String AIR_REFRESH_INTERVAL = "PT50M";

    private final RegionRepository regionRepository;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final AirQualityService airQualityService;
    private final TourClimateService tourClimateService;
    private final WeatherService weatherService;

    /** 랭킹·콘텐츠(6h TTL) 워밍 — 5h 주기. */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = SLOW_REFRESH_INTERVAL)
    public void warmSlowData() {
        List<Region> regions = loadRegions();
        if (regions.isEmpty()) {
            return;
        }
        // 각 단계를 격리한다 — 한 지역·랭킹의 예외가 나머지 워밍을 통째로 중단시키지 않게.
        try {
            // 원본이 완결된 달만 월 단위로 발행되므로, 지난달 집계를 이미 갖고 있으면 더 새 것은 없다(#193).
            // 예전에는 6시간 TTL 로 하루 네 번, 배포마다 또 물어 같은 답을 반복 확인했다.
            if (regionRankingService.hasLatest()) {
                log.info("홈 캐시 워밍 — 방문자 집계가 이미 최신이라 건너뜁니다");
            } else {
                regionRankingService.refresh();
            }
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 — 랭킹 갱신 실패(계속) cause={}", e.getClass().getSimpleName());
        }
        // degrade 는 지역마다 warn 이 찍히지만 그것만으로는 규모를 모른다 — 한 곳이 실패한 것과 여든 곳이
        // 실패한 것은 완전히 다른 사건인데 로그 모양은 같다. 회차마다 합계를 한 줄로 남긴다(#191).
        RegionContentProvider.RegionContents result = warmContent(regions);
        int warmed = result.byRegionId().size();
        int degraded = result.degraded();
        if (degraded > 0) {
            log.warn("홈 캐시 워밍(랭킹·콘텐츠) 완료 regions={}/{} — 외부 실패로 degrade {}건",
                    warmed, regions.size(), degraded);
            return;
        }
        log.info("홈 캐시 워밍(랭킹·콘텐츠) 완료 regions={}/{}", warmed, regions.size());
    }

    /**
     * 관광기후지수(6h TTL) 워밍 — 5h 주기(#130).
     *
     * <p>한 번 호출에 전 기간 × 전국이 통째로 오므로 워밍도 한 번이면 된다. 응답이 347KB·2.5초라 요청 경로에서
     * 콜드 미스를 만나면 사용자가 그 지연을 그대로 떠안는다.
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = SLOW_REFRESH_INTERVAL)
    public void warmTourClimate() {
        try {
            int days = tourClimateService.forecast().size();
            log.info("홈 캐시 워밍(관광기후지수) 완료 days={}", days);
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 — 관광기후지수 워밍 실패(계속)", e);
        }
    }

    /**
     * 중기예보(D+4~D+10) 워밍 — 키 공간이 광역 10구역으로 고정이라 관광기후지수와 같은 이유로 미리 데운다.
     *
     * <p>안 데우면 나흘 뒤 이후 날짜로 코스를 짜는 첫 사용자가 콜드 미스를 그대로 떠안는다. 10건뿐이라
     * 팬아웃 부담도 없다.
     */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = SLOW_REFRESH_INTERVAL)
    public void warmMidTermForecast() {
        try {
            weatherService.warmMidTerm();
            log.info("홈 캐시 워밍(중기예보) 완료");
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 — 중기예보 워밍 실패(계속)", e);
        }
    }

    /** 대기질(1h TTL) 워밍 — 50분 주기로 따로. 에어코리아는 시도당 수 초 걸려 요청 경로에서 부르면 홈이 느려진다. 시도 단위라 중복 제거. */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = AIR_REFRESH_INTERVAL)
    public void warmAirQuality() {
        List<String> sidos = loadRegions().stream().map(Region::getSido).distinct().toList();

        int warmed = 0;
        for (String sido : sidos) {
            try {
                if (airQualityService.byRegionSido(sido).isPresent()) {
                    warmed++;
                }
            } catch (RuntimeException e) {
                log.debug("대기질 워밍 실패 sido={}(계속)", sido, e);
            }
        }

        // **시도별로 찍지 않고 한 줄로 묶는다.** 에어코리아가 통째로 죽으면 시도 수만큼 같은 줄이 쌓여
        // 사용자 요청 로그를 밀어낸다. 반대로 아무것도 안 남기면 조용한 실패가 된다 — 건수 한 줄이 그 사이다.
        if (warmed < sidos.size()) {
            log.warn("홈 캐시 워밍(대기질) 일부 실패 — {}/{} 시도만 채웠습니다", warmed, sidos.size());
        } else {
            log.info("홈 캐시 워밍(대기질) 완료 sido={}", warmed);
        }
    }

    private List<Region> loadRegions() {
        try {
            return regionRepository.findAll();
        } catch (RuntimeException e) {
            log.warn("홈 캐시 워밍 — 지역 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 지역 콘텐츠를 데운다. 팬아웃(동시성 상한·지역별 예외 격리·전체 시간 상한)은 {@link RegionContentProvider} 가
     * 소유하므로 여기서는 넘기기만 한다.
     *
     * <p>워밍이 자기 풀을 따로 들지 않는 이유 — <b>요청 경로와 같은 메서드를 쓰기 위해서다.</b> 상수만 공유하면 한쪽만
     * 고쳐질 수 있지만, 메서드를 공유하면 동시성이 갈릴 여지가 없다(성능 규약).
     */
    private RegionContentProvider.RegionContents warmContent(List<Region> regions) {
        return regionContentProvider.contentForAll(
                regions, regions, RegionContentProvider.WARMING_FANOUT_DEADLINE);
    }
}
