package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.config.BatchBudgetProperties;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
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
import org.springframework.scheduling.annotation.Schedules;
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
 * <p><b>주기를 {@code fixedDelay} 에 맡기지 않는다.</b> 그건 프로세스가 살아 있는 동안의 간격이라 재배포하면
 * 처음부터 다시 센다 — "주 1회" 라고 적어 두고 배포마다 도는 일이 실제로 있었다(#226·#231). 갱신 주기는
 * cron 이 소유한다.
 *
 * <p><b>다만 부팅 확인은 따로 둔다</b>(#314). cron 만 두면 배치가 들어온 날부터 다음 1일까지 테이블이 빈 채로
 * 있고, 실제로 지역 상세·홈 추천 장소·홈 카드 부제가 함께 막혔다. 두 트리거의 역할이 다르다 — cron 은
 * "달이 바뀌면 갱신", 부팅은 "비어 있으면 채움" 이다.
 *
 * <p>둘 다 안전한 것은 <b>기준월 마커</b> 덕이다: 그 달치가 이미 있는 지역은 외부를 아예 안 부른다.
 * 여기에 {@code hasRunOn} 이 하루 한 번으로 더 조인다.
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

    /**
     * 부팅 후 첫 확인까지의 지연 — <b>cron 을 기다리다 화면이 비는 것을 막는다</b>(#314).
     *
     * <p>월 1회 cron 만 두면 배치가 머지된 날부터 다음 1일까지 {@code region_poi} 가 빈 채로 있다. 실제로
     * 그랬다 — 8/23 에 들어온 배치가 9/1 까지 안 돌아 지역 상세의 매력 포인트와 홈 추천 장소가 함께 비었고,
     * 그 테이블을 읽는 홈 카드 부제까지 일감이 0행이 됐다. 재기동해도 cron 은 그 시각에만 발화해 손쓸 수단이
     * 없었다.
     *
     * <p><b>거의 공짜다.</b> 아래 {@code hasFresh} 가 그 달치를 이미 받은 지역을 외부 호출 없이 건너뛴다 —
     * 첫 배포만 267콜이고 이후 배포는 0콜이다. 같은 날 재배포는 {@code hasRunOn} 이 막는다.
     *
     * <p><b>지연을 길게 둔 이유는 둘이다.</b> 부팅 직후 다른 적재 배치(30~90초)와 겹쳐 외부를 한꺼번에
     * 때리지 않게 하고, <b>통합 테스트가 끝난 뒤에 오게</b> 한다. 프로파일로는 못 막는다 —
     * {@code spring.profiles.active} 기본값이 {@code local} 이라 테스트 컨텍스트에서도 {@code local} 이
     * 켜져 있어 {@code @Profile("local | prod")} 가 아무것도 거르지 않는다(실측으로 확인했다).
     */
    private static final String BOOT_CHECK_DELAY = "PT120S";

    /**
     * 부팅 확인의 반복 간격. 실질적으로는 재발화하지 않는 값이다 — 주기 갱신은 {@link #MONTHLY_AT_DAWN} 이
     * 소유하고, 이 트리거는 "부팅했는데 비어 있으면 채운다" 만 맡는다.
     */
    private static final String BOOT_CHECK_INTERVAL = "P30D";

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
     * 외부가 콘텐츠 타입을 안 줬을 때 넣는 값.
     *
     * <p>TourAPI 의 유효한 타입은 12·14·15·25·28·32·38·39 라 {@code 0} 은 그중 어느 것도 아니다. 즉
     * 이 값은 타입이 아니라 <b>"모른다"</b> 는 뜻이다. 리터럴로 두면 이 컬럼을 읽는 쪽이 실제 타입 코드로
     * 오해한다.
     */
    private static final int UNKNOWN_CONTENT_TYPE = 0;

    /**
     * 타입별로 나눠 부른다 — 전체타입 한 번으로는 <b>맛집·숙박이 과소표집된다</b>.
     *
     * <p>등록 수가 적은 지역에서 볼거리에 밀려 끼니와 잠자리가 목록에서 빠진다. {@code RegionPoiService} 가
     * 같은 이유로 같은 방식을 쓴다. {@code null} 은 전체타입이고, 체험(EX)은 그 안에 섞여 온다.
     */
    private static final List<Integer> CONTENT_TYPES = List.of(39, 32);

    private final RegionRepository regionRepository;
    private final RegionPoiRepository regionPoiRepository;
    private final TourApiClient tourApiClient;
    private final BatchRunRepository batchRunRepository;
    private final BatchBudgetProperties batchBudget;

    @Schedules({
        @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID),
        @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    })
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            LocalDate today = LocalDate.now(SERVICE_ZONE);
            // 확인과 기록을 한 문장으로 묶는다 — 트리거가 둘이라 "확인 → 267콜 → 기록" 사이의 창에
            // 다른 트리거가 들어오면 같은 날 두 번 쏜다. 선점에 성공한 실행만 아래로 내려간다.
            if (!batchRunRepository.tryStartOn(BATCH_NAME, today, LocalDateTime.now(SERVICE_ZONE))) {
                log.info("지역 장소 풀을 오늘 이미 돌렸거나 다른 트리거가 선점해 갱신을 건너뜁니다 date={}", today);
                return;
            }
            // 선점이 곧 기록이다 — 아래가 실패해도 시각이 남아 같은 날 재부팅이 267콜을 다시 쏘지 않는다.
            refresh(YearMonth.from(today));
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
     * 외부 응답을 우리 장소로 옮긴다 — <b>분류가 안 서면 담지 않는다</b>.
     *
     * <p>{@code lclsSystm1} 이 비었거나 우리 네 칩 어디에도 안 걸리는 값이면 화면에 걸 자리가 없다.
     * {@code ALL} 로 떨어뜨려 담으면 어느 칩을 눌러도 나오는 장소가 생긴다.
     */
    /**
     * {@code RegionPoi} 가 필수로 요구하는 값인지 — 없으면 이 장소를 담지 않는다.
     *
     * <p><b>{@code null} 만 걸러선 부족하다.</b> 엔티티가 {@code contentId}·{@code title} 을 공백까지
     * 막으므로(불변식), 빈 문자열이 여기를 통과하면 조립에서 예외가 난다. 그 예외는 지역 단위 catch 에
     * 걸려 <b>그 지역이 통째로 안 채워진다</b> — 장소 하나 때문에 지역 하나가 빈다.
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RegionPoi toRegionPoi(long regionId, TourPoi poi, YearMonth baseYm, LocalDateTime now) {
        Category category = Category.fromLclsSystm1(poi.lclsSystm1()).orElse(null);
        if (category == null || isBlank(poi.contentId()) || isBlank(poi.title())) {
            return null;
        }
        return RegionPoi.builder()
                .regionId(regionId)
                .contentId(poi.contentId())
                .contentTypeId(poi.contentTypeId() == null ? UNKNOWN_CONTENT_TYPE : poi.contentTypeId())
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

}
