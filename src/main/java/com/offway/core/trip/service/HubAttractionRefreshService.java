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
     * 발행 지연을 감안해 물러설 개월 수.
     *
     * <p>이번 달 것은 아직 없을 수 있다. 지난달부터 시작해 빈 결과면 이전 달로 물러선다 — 고정 지연을 가정하면
     * 발행 공백에 걸려 <b>조용히</b> 빈 결과가 되고, 전 지역 중심 관광지가 사라진다.
     */
    private static final int MAX_MONTHS_BACK = 3;

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
        YearMonth month = publishedMonth(regions.getFirst());
        if (month == null) {
            log.warn("중심 관광지 최근 {}개월이 모두 비었습니다 — 갱신을 건너뜁니다", MAX_MONTHS_BACK);
            return;
        }

        int filled = 0;
        int failed = 0;
        for (Region region : regions) {
            try {
                List<HubAttractionItem> items =
                        hubAttractionClient.findByRegion(region.getLegalCode(), month, ROWS_PER_REGION);
                if (items.isEmpty()) {
                    // 성공 코드에 빈 결과가 오는 API 다 — 덮지 않고 이전 값을 남긴다.
                    failed++;
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
        if (failed > 0) {
            log.warn("중심 관광지 갱신 완료 baseYm={} 성공={}/{} — 실패 {}건은 이전 값 유지",
                    month, filled, regions.size(), failed);
            return;
        }
        log.info("중심 관광지 갱신 완료 baseYm={} 지역={}/{}", month, filled, regions.size());
    }

    /**
     * 실제로 발행된 달을 찾는다 — 지역 하나로 탐색해 89번 반복하지 않는다.
     *
     * @return 발행된 달. {@value #MAX_MONTHS_BACK}개월을 물러서도 없으면 null
     */
    private YearMonth publishedMonth(Region probe) {
        YearMonth month = newestPossibleMonth();
        for (int back = 0; back < MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            try {
                if (!hubAttractionClient.findByRegion(probe.getLegalCode(), month, 1).isEmpty()) {
                    return month;
                }
            } catch (TourApiException e) {
                log.warn("중심 관광지 발행월 탐색 실패 month={} cause={}", month, e.getClass().getSimpleName());
                return null;
            }
            log.info("중심 관광지 {} 미발행 — 이전 달로 물러섭니다", month);
        }
        return null;
    }

    /** 존재할 수 있는 가장 새 달. 이번 달은 아직 집계 중이라 지난달이 최선이다. */
    private static YearMonth newestPossibleMonth() {
        return YearMonth.from(LocalDate.now()).minusMonths(1);
    }

    /** 저장분이 그 달 것인가 — 하나만 확인하면 된다. 갱신은 전 지역을 같은 달로 채운다. */
    private boolean hasMonth(YearMonth month) {
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return false;
        }
        return hubAttractionRepository.findByRegionId(regions.getFirst().getId()).stream()
                .findFirst()
                .filter(attraction -> !attraction.baseMonth().isBefore(month))
                .isPresent();
    }

    /** 지역의 중심 관광지 — 순위 오름차순. 아직 적재 전이면 빈 목록이다. */
    public List<HubAttraction> forRegion(Long regionId) {
        return hubAttractionRepository.findByRegionId(regionId);
    }
}
