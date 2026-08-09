package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.HubAttractionClient;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 중심 관광지를 받아 DB 에 적재한다(#185).
 *
 * <p><b>요청 경로에서 부르지 않는다.</b> 89개 지역 × 1회를 요청마다 하면 외부 한도가 금방 마른다. 원본은 월 단위로
 * 갱신되므로 하루 한 번 확인하고, 이미 최신이면 외부를 아예 부르지 않는다(#193 과 같은 원칙).
 *
 * <p><b>순차로 부른다.</b> 병렬로 던지면 초당 호출이 폭증해 429 를 맞는다 — 부팅 워밍에서 실제로 그랬다(#191).
 * 월 1회 배경 작업이라 89건을 순서대로 도는 시간은 아무도 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubAttractionRefreshService {

    /** 부팅 후 첫 확인까지 지연 — 기동·헬스체크를 방해하지 않게. */
    private static final String INITIAL_DELAY = "PT30S";

    /** 확인 주기. 원본이 월 단위라 하루 한 번이면 충분하고, 이미 최신이면 외부를 안 부른다. */
    private static final String CHECK_INTERVAL = "P1D";

    /** 지자체당 보관 순위 수. 원본은 100위까지 주지만 카드·코스에 쓰는 것은 상위 소수다. */
    private static final int ROWS_PER_REGION = 30;

    /**
     * 발행 지연을 감안해 물러설 개월 수 — 시작월 <b>포함</b> {@code MAX_MONTHS_BACK + 1}개 달을 확인한다.
     *
     * <p>이번 달 것은 아직 없을 수 있다. 지난달부터 시작해 빈 결과면 이전 달로 물러선다 — 고정 지연을 가정하면
     * 발행 공백에 걸려 <b>조용히</b> 빈 결과가 되고, 전 지역 중심 관광지가 사라진다.
     */
    private static final int MAX_MONTHS_BACK = 3;

    /**
     * 발행월을 판단할 때 물어보는 지역 수.
     *
     * <p><b>한 곳으로 판단하면 안 된다.</b> 그 지역만 데이터가 없어도 "미발행" 으로 읽혀 89곳 전체 갱신이
     * 조용히 스킵된다 — 예외도 로그도 없이 대표 사진이 낡은 채로 남는다. 몇 곳을 확인해 한 곳이라도 있으면
     * 그 달은 발행된 것으로 본다.
     */
    private static final int PUBLISH_PROBE_REGIONS = 3;

    private final HubAttractionClient hubAttractionClient;
    private final HubAttractionRepository hubAttractionRepository;
    private final RegionRepository regionRepository;

    /** 하루 한 번 확인 — 이미 최신이면 외부 호출 없이 끝난다. */
    @Scheduled(initialDelayString = INITIAL_DELAY, fixedDelayString = CHECK_INTERVAL)
    public void refreshIfStale() {
        YearMonth newest = newestPossibleMonth();
        if (hasMonth(newest)) {
            log.info("중심 관광지가 이미 최신({})이라 갱신을 건너뜁니다", newest);
            return;
        }
        refresh();
    }

    /**
     * 전 지역을 다시 받아 적재한다.
     *
     * <p>지역마다 격리한다 — 한 곳이 실패해도 나머지는 채운다. 실패한 지역은 <b>이전 값을 그대로 둔다</b>.
     * 빈 목록으로 덮으면 그 지역 카드에서 대표 사진과 볼거리가 통째로 사라진다.
     */
    public void refresh() {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return;
        }
        YearMonth month = publishedMonth(regions);
        if (month == null) {
            log.warn("중심 관광지 최근 {}개월이 모두 비었습니다 — 갱신을 건너뜁니다", MAX_MONTHS_BACK);
            return;
        }

        int filled = 0;
        int empty = 0;
        int failed = 0;
        for (Region region : regions) {
            try {
                List<HubAttractionItem> items =
                        hubAttractionClient.findByRegion(region.getLegalCode(), month, ROWS_PER_REGION);
                if (items.isEmpty()) {
                    // 성공 코드에 빈 결과가 오는 API 다 — 덮지 않고 이전 값을 남긴다. 집계만으로는 어느 지역이
                    // 왜 안 채워졌는지 모르므로 지역·달을 남긴다(발행 지연 vs 그 지역만 없음을 가른다).
                    empty++;
                    log.warn("중심 관광지 빈 응답 — 이전 값을 유지합니다 regionId={} baseYm={}", region.getId(), month);
                    continue;
                }
                hubAttractionRepository.replaceRegion(
                        region.getId(), items.stream().map(item -> item.toEntity(region.getId(), month)).toList());
                filled++;
            } catch (TourApiException e) {
                failed++;
                log.warn("중심 관광지 갱신 실패 — 이전 값을 유지합니다 regionId={} cause={}",
                        region.getId(), e.getClass().getSimpleName());
            }
        }
        if (empty + failed > 0) {
            log.warn("중심 관광지 갱신 완료 baseYm={} 성공={}/{} — 빈 응답 {}건·실패 {}건은 이전 값 유지",
                    month, filled, regions.size(), empty, failed);
            return;
        }
        log.info("중심 관광지 갱신 완료 baseYm={} 지역={}/{}", month, filled, regions.size());
    }

    /**
     * 실제로 발행된 달을 찾는다 — 89곳을 다 물어보지 않고 표본 몇 곳으로 가린다.
     *
     * <p>표본을 {@value #PUBLISH_PROBE_REGIONS}곳 두는 이유는 위 상수에 적었다. 한 곳이라도 결과가 있으면
     * 그 달은 발행된 것이다.
     *
     * @return 발행된 달. {@value #MAX_MONTHS_BACK}개월을 물러서도 없으면 null
     */
    private YearMonth publishedMonth(List<Region> regions) {
        List<Region> probes = regions.subList(0, Math.min(PUBLISH_PROBE_REGIONS, regions.size()));
        YearMonth month = newestPossibleMonth();
        // 경계를 포함한다 — 상수가 "물러설 개월 수" 이므로 시작월에서 그만큼 물러선 달까지 봐야 이름과 맞는다.
        for (int back = 0; back <= MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            for (Region probe : probes) {
                try {
                    if (!hubAttractionClient.findByRegion(probe.getLegalCode(), month, 1).isEmpty()) {
                        return month;
                    }
                } catch (TourApiException e) {
                    // 다음 표본으로 넘어간다. 여기서 포기하면 표본을 여럿 두는 의미가 없어진다 —
                    // 한 지역의 이상으로 89곳 갱신이 통째로 스킵되는 것을 막으려고 둔 장치다.
                    log.warn("중심 관광지 발행월 탐색 실패 month={} regionId={} cause={}",
                            month, probe.getId(), e.getClass().getSimpleName());
                }
            }
            log.info("중심 관광지 {} 미발행(표본 {}곳) — 이전 달로 물러섭니다", month, probes.size());
        }
        return null;
    }

    /** 존재할 수 있는 가장 새 달. 이번 달은 아직 집계 중이라 지난달이 최선이다. */
    private static YearMonth newestPossibleMonth() {
        return YearMonth.from(LocalDate.now()).minusMonths(1);
    }

    /**
     * <b>모든</b> 지역이 그 달 데이터를 갖고 있는가.
     *
     * <p>한 곳만 보면 안 된다. 앞선 갱신에서 첫 지역만 성공하고 나머지가 빈 응답·실패로 남았을 때, 다음 스케줄이
     * "첫 지역이 최신" 이라는 이유로 전체를 건너뛴다 — 실패한 지역은 <b>영영 재시도되지 않는다.</b>
     *
     * <p>대신 원본에 데이터가 아예 없는 지역이 하나라도 있으면 매일 전량을 다시 묻게 된다. 하루 한 번 89건이라
     * 한도(#193)에는 여유가 있고, 그 지역이 어디인지는 갱신 때마다 빈 응답 warn 으로 드러난다 — 조용히
     * 스킵되는 쪽보다 낫다.
     */
    private boolean hasMonth(YearMonth month) {
        List<Long> regionIds = regionRepository.findAll().stream().map(Region::getId).toList();
        if (regionIds.isEmpty()) {
            return false;
        }
        return hubAttractionRepository.countRegionsWithMonthAtLeast(regionIds, month) == regionIds.size();
    }

    /** 지역의 중심 관광지 — 순위 오름차순. 아직 적재 전이면 빈 목록이다. */
    public List<HubAttraction> forRegion(Long regionId) {
        return hubAttractionRepository.findByRegionId(regionId);
    }
}
