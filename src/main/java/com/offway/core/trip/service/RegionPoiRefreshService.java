package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.config.BatchBudgetProperties;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.RegionPoiRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 지역별 장소 풀을 <b>월 1회</b> 받아 DB 에 적재한다(#304).
 *
 * <h2>요청 경로에서 부르지 않는 이유가 이 클래스의 존재 이유다</h2>
 *
 * <p>TourAPI 는 개발계정 <b>일 1,000건</b>이고 관광빅데이터·데이터랩과 <b>같은 계정을 공유</b>한다.
 * 지역 상세를 열 때마다 부르면 지역당 3콜이라 사용자 몇 명으로 하루 한도가 마르고, 그러면 코스 생성이
 * 먼저 죽는다. 조회는 DB 만 읽고, 외부는 여기서만 만난다.
 *
 * <pre>
 * 지역당 3콜(전체타입 · 맛집 · 숙박) × 89곳 = 267건
 * 월 1회  →  하루 평균 9건 (한도의 1%)
 * </pre>
 *
 * <p><b>매일 돌 이유도 없다.</b> 지역별 POI 목록은 새 관광지가 등록되며 느리게 늘 뿐이라, 어제와 오늘이
 * 다를 일이 거의 없다.
 *
 * <p><b>{@code fixedDelay} 를 쓰지 않는다.</b> 그건 프로세스가 살아 있는 동안의 간격이라 재배포하면 주기가
 * 처음부터 다시 센다 — "주 1회" 라고 적어 두고 배포마다 도는 일이 실제로 있었다(#226·#231). 대신
 * <b>기준월 마커</b>로 가른다: 그 달치가 이미 있는 지역은 외부를 아예 안 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionPoiRefreshService {

    /**
     * 매달 1일 새벽 4시에 확인한다.
     *
     * <p>사용자가 거의 없는 시각이라 267콜이 그날 사용자 몫을 밀어내지 않는다. 날짜를 1일로 둔 것은
     * 기준월과 실행일을 맞추기 위해서다 — 월말에 돌면 다음 달 첫 조회가 이미 낡은 값을 본다.
     */
    private static final String MONTHLY_AT_DAWN = "0 0 4 1 * *";

    /** 서비스 기준 시간대. 갱신 기준월은 한국 달력 기준이라 서버 로케일에 맡기지 않는다. */
    private static final String SERVICE_ZONE_ID = "Asia/Seoul";

    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /** 외부 사용량이 누구 몫인지 가른다(#285) — 배치가 태운 것과 사용자가 태운 것을 섞지 않는다. */
    private static final Caller CALLER = Caller.of("지역장소배치");

    /** 하루에 한 번만 시도한다는 표식. 실패해도 남겨 재부팅이 같은 날 다시 쏘지 않게 한다. */
    private static final String BATCH_NAME = "region-poi-refresh";

    /**
     * 지역·타입당 끌어오는 행 수.
     *
     * <p>{@code RegionPoiService} 의 후보 수와 같은 값이다. 화면이 쓰는 것은 10개 남짓이지만, <b>사진 있는
     * 것만</b> 골라 담으므로 원본이 넉넉해야 그 10개가 채워진다. 인구감소지역은 등록 수 자체가 적다.
     */
    private static final int ROWS_PER_CALL = 100;

    /**
     * 타입별로 나눠 부른다 — 전체타입 한 번으로는 <b>맛집·숙박이 과소표집된다</b>.
     *
     * <p>등록 수가 적은 지역에서 볼거리에 밀려 끼니와 잠자리가 목록에서 빠진다. {@code RegionPoiService} 가
     * 같은 이유로 같은 방식을 쓴다. {@code null} 은 전체타입이고, 체험(EX)은 그 안에 섞여 온다.
     */
    private static final List<Integer> CONTENT_TYPES = new ArrayList<>(List.of(39, 32));

    /**
     * 사진 있는 장소가 이 수에 못 미치면 <b>국가유산으로 보충한다</b>.
     *
     * <p>시안이 매력 포인트 장소를 "최소 2개" 로 잡았다. TourAPI 만으로는 그 최소가 안 채워지는 지역이
     * 있다 — 인구감소지역은 등록 수 자체가 적고, 등록돼 있어도 사진이 없는 경우가 많다.
     */
    private static final int MIN_SHOWABLE = 2;

    /** 보충으로 더할 국가유산 수 상한 — 화면이 쓰는 10개를 넘겨 담을 이유가 없다. */
    private static final int HERITAGE_SUPPLEMENT_ROWS = 10;

    private final RegionRepository regionRepository;
    private final RegionPoiRepository regionPoiRepository;
    private final TourApiClient tourApiClient;
    private final BatchRunRepository batchRunRepository;
    private final BatchBudgetProperties batchBudget;
    private final HeritagePlaceRepository heritagePlaceRepository;

    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            LocalDate today = LocalDate.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunOn(BATCH_NAME, today)) {
                log.info("지역 장소 풀을 오늘 이미 돌려 갱신을 건너뜁니다 date={}", today);
                return;
            }
            try {
                refresh(YearMonth.from(today));
            } finally {
                // 실패해도 남긴다 — 안 남기면 같은 날 재부팅마다 267콜을 다시 쏜다.
                batchRunRepository.markStarted(BATCH_NAME, LocalDateTime.now(SERVICE_ZONE));
            }
        });
    }

    /**
     * 기준월 기준으로 아직 안 받은 지역만 채운다.
     *
     * <p>기준월을 인자로 받는 이유는 <b>"이번 달" 이 언제인지를 호출자가 정할 수 있어야</b> 하기 때문이다.
     * 스케줄러는 오늘을 넘기고, 테스트는 고정된 달을 넘긴다.
     *
     * <p><b>지역마다 격리한다.</b> 한 곳이 실패해도 나머지는 채우고, 실패한 지역은 <b>이전 값을 그대로
     * 둔다</b> — 빈 목록으로 덮으면 그 지역 상세가 통째로 빈다.
     *
     * @return 이 실행으로 새로 채운 지역 수
     */
    public int refresh(YearMonth baseYm) {
        List<Region> all = regionRepository.findAll();
        if (all.isEmpty()) {
            log.info("지역 장소 풀 — 지역 마스터가 비어 있어 건너뜁니다");
            return 0;
        }
        // 로컬은 한 회차에 몇 곳만 채운다(#254) — 로컬과 운영이 같은 키를 쓰므로 각자 하루치를 태운다.
        List<Region> regions = batchBudget.limit(all);
        if (batchBudget.limits(all.size())) {
            log.info("지역 장소 풀 — 이번 회차는 {}/{}곳만 갱신합니다(로컬 예산)", regions.size(), all.size());
        }

        int filled = 0;
        int skipped = 0;
        int failed = 0;
        for (Region region : regions) {
            if (regionPoiRepository.hasFresh(region.getId(), baseYm)) {
                skipped++;
                continue;
            }
            try {
                filled += fill(region, baseYm) ? 1 : 0;
            } catch (RuntimeException e) {
                // 한 지역의 실패로 나머지를 버리지 않는다. 다만 조용히 넘기지도 않는다.
                failed++;
                log.warn("지역 장소 풀 적재 실패 regionId={} cause={}",
                        region.getId(), e.getClass().getSimpleName());
            }
        }
        // 셋을 함께 남겨야 "이미 최신이라 안 불렀다" 와 "대상이 없다" 와 "실패했다" 가 로그만으로 갈린다.
        log.info("지역 장소 풀 갱신 baseYm={} 대상={}곳 새로 채움={}곳 이미 최신={}곳 실패={}곳",
                baseYm, regions.size(), filled, skipped, failed);
        return filled;
    }

    /**
     * 지역 하나를 받아 통째로 갈아 끼운다.
     *
     * <p><b>비면 덮지 않는다.</b> 외부가 실패해 0건이 왔는데 그대로 넣으면 멀쩡하던 지역 상세가 빈다.
     * 이전 값을 두고 다음 달을 기다리는 편이 낫다 — 낡은 목록이 없는 목록보다 낫기 때문이다.
     *
     * @return 실제로 갈아 끼웠으면 true
     */
    private boolean fill(Region region, YearMonth baseYm) {
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        // contentId 로 중복을 접는다 — 전체타입 조회와 타입별 조회가 같은 장소를 함께 물고 온다.
        Map<String, RegionPoi> byContentId = new LinkedHashMap<>();

        List<Integer> types = new ArrayList<>();
        types.add(null); // 전체타입 — 관광지·체험이 여기서 온다
        types.addAll(CONTENT_TYPES);

        for (Integer contentTypeId : types) {
            TourPoiResult result =
                    tourApiClient.findByArea(region.getAreaCode(), region.getSigunguCode(), contentTypeId, ROWS_PER_CALL);
            for (TourPoi poi : result.items()) {
                RegionPoi mapped = toRegionPoi(region.getId(), poi, baseYm, now);
                if (mapped != null) {
                    byContentId.putIfAbsent(mapped.getContentId(), mapped);
                }
            }
        }

        int fromTour = (int) byContentId.values().stream().filter(RegionPoi::showable).count();
        if (fromTour < MIN_SHOWABLE) {
            supplementWithHeritage(region.getId(), baseYm, now, byContentId);
        }

        if (byContentId.isEmpty()) {
            log.warn("지역 장소 풀 — 받아온 장소가 0건이라 이전 값을 그대로 둡니다 regionId={}", region.getId());
            return false;
        }

        List<RegionPoi> pois = List.copyOf(byContentId.values());
        regionPoiRepository.replaceRegion(region.getId(), pois);
        log.debug("지역 장소 풀 적재 regionId={} {}건 (사진 있음 {}건)",
                region.getId(), pois.size(), pois.stream().filter(RegionPoi::showable).count());
        return true;
    }

    /**
     * 사진 있는 장소가 모자란 지역을 <b>국가유산으로 메운다</b>(#304) — <b>외부 호출이 없다</b>.
     *
     * <p>국가유산 3,437건은 이미 DB 에 있고 <b>사진 96%·설명 98%</b> 를 들고 있다. TourAPI 를 더 부르면
     * 한도를 태우지만 이쪽은 한 번의 DB 조회다. 코스 생성이 같은 이유로 같은 보충을 이미 쓴다(#144·#160).
     *
     * <p><b>모자랄 때만 쓴다.</b> 넉넉한 지역까지 섞으면 관광지 쪽이 국가유산으로 밀린다 — TourAPI 장소가
     * 사진·소개가 더 두껍고 상세({@code GET /pois/{contentId}})도 풍부하다.
     *
     * <p>분류는 {@link Category#SIGHT} 다. 국가유산은 그 자체가 볼거리이고 숙박·맛집일 수 없다.
     */
    private void supplementWithHeritage(
            long regionId, YearMonth baseYm, LocalDateTime now, Map<String, RegionPoi> byContentId) {
        List<HeritagePlace> heritages =
                heritagePlaceRepository.findVisitableCandidates(regionId, HERITAGE_SUPPLEMENT_ROWS);
        int added = 0;
        for (HeritagePlace heritage : heritages) {
            // 사진 없는 국가유산으로 메우면 회색 판이 늘 뿐이다 — 보충의 목적이 사진이다.
            if (heritage.getImageUrl() == null || heritage.getImageUrl().isBlank()) {
                continue;
            }
            RegionPoi poi = RegionPoi.builder()
                    .regionId(regionId)
                    .contentId(heritage.publicId())
                    .contentTypeId(0) // TourAPI 콘텐츠가 아니다
                    .category(Category.SIGHT)
                    .title(heritage.getName())
                    .imageUrl(heritage.getImageUrl())
                    .address(heritage.getAddress())
                    .baseYm(baseYm)
                    .fetchedAt(now)
                    .build();
            if (byContentId.putIfAbsent(poi.getContentId(), poi) == null) {
                added++;
            }
        }
        // degrade 한 사실을 남긴다 — 보충이 조용히 일어나면 TourAPI 쪽 공백을 아무도 모른다.
        log.info("지역 장소 풀 — 국가유산으로 보충 regionId={} {}건 추가", regionId, added);
    }

    /**
     * 외부 응답을 우리 장소로 옮긴다 — <b>분류가 안 서면 담지 않는다</b>.
     *
     * <p>{@code lclsSystm1} 이 비었거나 우리 네 칩 어디에도 안 걸리는 값이면 화면에 걸 자리가 없다.
     * {@code ALL} 로 떨어뜨려 담으면 어느 칩을 눌러도 나오는 장소가 생긴다.
     */
    private static RegionPoi toRegionPoi(long regionId, TourPoi poi, YearMonth baseYm, LocalDateTime now) {
        Category category = categoryOf(poi.lclsSystm1());
        if (category == null || poi.contentId() == null || poi.title() == null || poi.title().isBlank()) {
            return null;
        }
        return RegionPoi.builder()
                .regionId(regionId)
                .contentId(poi.contentId())
                .contentTypeId(poi.contentTypeId() == null ? 0 : poi.contentTypeId())
                .category(category)
                .title(poi.title())
                .imageUrl(poi.firstImage())
                .address(poi.address())
                .lat(poi.lat())
                .lng(poi.lng())
                .tel(poi.tel())
                .baseYm(baseYm)
                .fetchedAt(now)
                .build();
    }

    /** 필터칩과 같은 규칙으로 가른다({@link Category}) — 판정이 둘로 갈리면 칩 개수와 목록이 어긋난다. */
    private static Category categoryOf(String lclsSystm1) {
        if (lclsSystm1 == null || lclsSystm1.isBlank()) {
            return null;
        }
        for (Category candidate : Category.values()) {
            if (candidate != Category.ALL && candidate.includes(lclsSystm1)) {
                return candidate;
            }
        }
        return null;
    }
}
