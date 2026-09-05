package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.infrastructure.festival.FestivalStandardClient;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 전국문화축제표준데이터를 받아 축제 풀을 채운다(#433).
 *
 * <h2>왜 배치인가</h2>
 *
 * <p>선택지가 셋이었다 — 요청 때 외부 호출 / 배치가 API 를 불러 DB 적재 / 빌드 타임에 파일을 리소스로.
 * 가르는 기준은 <b>원본이 배포보다 자주 바뀌나</b> 다.
 *
 * <p>인허가 장소(121,393건)는 원본이 870MB 에 좌표계 변환까지 필요하고 분기 갱신이라 파일로 굳혔다.
 * 축제는 반대다 — <b>기간이 있는 값이라 낡으면 틀리고</b>(끝난 축제를 코스에 넣으면 헛걸음이다),
 * 원본이 <b>매월</b> 병합되며, 한도가 TourAPI 와 별개로 넉넉하다(10,000/일).
 *
 * <h2>전국을 한 번에 받아 지역으로 나눈다</h2>
 *
 * <p>지역별로 부르면 회차마다 89번이다. 원본이 전국 1,305건이라 <b>페이지 수만큼</b>이면 되고, 첫
 * 응답의 {@code totalCount} 가 그 수를 즉시 알려준다.
 *
 * <h2>온전히 받았을 때만 정리한다</h2>
 *
 * <p>취소된 축제는 목록에서 빠지므로 upsert 만으로는 옛 행이 남는다. 다만 페이지가 하나라도 실패하면
 * <b>"이번에 안 온 것 = 취소됨" 이 성립하지 않는다</b> — 그때 지우면 멀쩡한 축제를 우리가 없앤다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalPlaceRefreshService {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매월 6일 새벽 4시 50분.
     *
     * <p>원본이 <b>매월 초</b> 병합되므로 며칠 지난 시점에 받는다. 다른 배치와 시각을 벌렸다 —
     * 지역 장소 풀이 매월 1일 04:00, 축제 기간이 화요일 04:20 이다.
     */
    private static final String MONTHLY_AT_DAWN = "0 50 4 6 * *";

    /**
     * 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 재배포하면 주기가 처음부터 다시 센다(#226·#231). 아래 마커가 그것을 막는다.
     */
    private static final String BOOT_CHECK_DELAY = "PT300S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    /** 이 주기 안에 이미 돌았으면 건너뛴다 — 재배포가 한도를 다시 태우지 않게. */
    private static final Duration RUN_INTERVAL = Duration.ofDays(25);

    private static final String BATCH_NAME = "festival-place-refresh";

    private static final Caller CALLER = Caller.of("축제풀배치");

    /** 한 페이지 건수. 전국 1,305건이라 100이면 14페이지다. */
    private static final int ROWS_PER_PAGE = 100;

    private static final int FIRST_PAGE = 1;

    /**
     * 한 회차 페이지 상한 — 폭주 안전장치.
     *
     * <p>전국 1,305건 기준 14페이지다(실측: 포털 그리드 다운로드로 전량 확인, 2026-09-04). 20이면
     * 원본이 절반 가까이 늘어도 견디고, 넘으면 무엇을 못 받았는지 로그로 남긴다.
     */
    private static final int MAX_PAGES = 20;

    /** 집계 전체의 시간 상한. 호출 하나의 timeout 이 페이지 수만큼 곱해지는 것을 막는다. */
    private static final Duration TOTAL_DEADLINE = Duration.ofMinutes(3);

    private final FestivalStandardClient festivalStandardClient;
    private final FestivalPlaceRepository festivalPlaceRepository;
    private final RegionQuery regionQuery;
    private final BatchRunRepository batchRunRepository;

    /** 배치를 멈추거나 한도 상한을 거는 스위치(#403). */
    private final ExternalApiBatchPolicy batchPolicy;

    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.FESTIVAL_STANDARD)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "축제가 왜 안 채워지지" 가 된다.
                log.info("축제 풀 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(RUN_INTERVAL))) {
                log.info("축제 풀을 최근 {}일 안에 이미 받아 갱신을 건너뜁니다", RUN_INTERVAL.toDays());
                return;
            }
            // **온전히 받은 회차만 기록한다.** 저장 건수만 보면 안 된다 — 둘째 페이지가 깨져도 첫
            // 페이지 것은 저장되므로 건수가 양수이고, 그걸로 마커를 남기면 반쪽짜리 목록을 들고
            // 25일을 버틴다. 부르기 전에 적으면 첫 페이지가 깨진 날에도 같은 일이 생긴다.
            RefreshOutcome outcome = refresh();
            if (outcome.complete() && outcome.saved() > 0) {
                batchRunRepository.markStarted(BATCH_NAME, now);
            }
        });
    }

    /**
     * 한 회차의 결과.
     *
     * <p><b>저장 건수와 회차 완결성은 다른 값이다.</b> 둘째 페이지가 깨져도 첫 페이지 것은 저장되므로
     * 건수만 보고 "다 됐다" 고 판정하면, 마커가 남아 다음 갱신이 25일 막힌다 — 반쪽짜리 축제 목록을
     * 그동안 그대로 쓰게 된다.
     *
     * @param saved 저장한 건수
     * @param complete 페이지를 하나도 빠뜨리지 않고 받았나
     */
    public record RefreshOutcome(int saved, boolean complete) {

        private static final RefreshOutcome NOTHING = new RefreshOutcome(0, false);

        /** 이번 회차는 없던 일이다 — 마커도 남기지 않는다. */
        static RefreshOutcome nothing() {
            return NOTHING;
        }
    }

    /**
     * 전국 축제를 받아 우리 89곳 것만 저장한다.
     *
     * @return 저장 건수와 회차 완결성
     */
    public RefreshOutcome refresh() {
        // **초 단위로 자른다.** fetched_at 이 DATETIME(소수점 없음)이라, 나노초가 붙은 값을 넣으면
        // MySQL 이 반올림하거나 버린다. 그 결과가 저장값보다 커지는 순간 아래 취소 정리가 **방금 넣은
        // 축제를 지운다** — 실행 시각의 밀리초에 따라 되기도 안 되기도 하는, 되돌릴 수 없는 손실이다.
        return refresh(LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS));
    }

    /**
     * 회차 시각을 지정해 받는다.
     *
     * <p><b>시각이 인자인 이유</b>는 그것이 곧 "이번 회차" 의 표식이기 때문이다. 이 값으로 저장하고
     * 이 값보다 오래된 행을 지우므로, 두 회차가 같은 초에 돌면 뒤 회차가 앞 회차를 못 걷어낸다.
     * 운영은 25일 간격이라 닿지 않는 경계지만, 테스트가 시계에 기대지 않으려면 열려 있어야 한다.
     */
    public RefreshOutcome refresh(LocalDateTime fetchedAt) {
        Map<String, Long> regionIdBySigungu = regionIdsBySigungu();
        if (regionIdBySigungu.isEmpty()) {
            log.info("축제 풀 — 지역 마스터가 비어 있어 건너뜁니다");
            return RefreshOutcome.nothing();
        }

        LocalDateTime deadline = LocalDateTime.now(SERVICE_ZONE).plus(TOTAL_DEADLINE);
        StandardFestivalResult first;
        try {
            first = festivalStandardClient.findAll(FIRST_PAGE, ROWS_PER_PAGE, TOTAL_DEADLINE);
        } catch (RuntimeException e) {
            // 첫 페이지가 깨지면 이번 회차는 없던 일이다. 기존 값을 덮지 않으므로 화면은 그대로다.
            log.warn("축제 풀 첫 페이지 조회 실패 — 이번 회차를 건너뜁니다 cause={}", RootCause.label(e));
            return RefreshOutcome.nothing();
        }

        int totalPages = first.totalPages(ROWS_PER_PAGE);
        int pagesToRead = Math.min(totalPages, MAX_PAGES);
        // **이 줄이 실측이다.** 첫 호출 하나로 전체 건수와 남은 호출 수가 확정된다.
        log.info("축제 풀 조회 시작 전체={}건 전체페이지={} 읽을페이지={}",
                first.totalCount(), totalPages, pagesToRead);
        if (totalPages > MAX_PAGES) {
            // 조용히 자르지 않는다. 무엇을 못 받았는지 남겨야 상한을 다시 정할 수 있다.
            log.warn("축제가 상한보다 많습니다 — {}페이지 중 {}페이지만 받습니다(약 {}건 누락). 상한을 다시 보세요",
                    totalPages, MAX_PAGES, (totalPages - MAX_PAGES) * ROWS_PER_PAGE);
        }

        List<StandardFestival> collected = new ArrayList<>(first.items());
        int failedPages = 0;
        for (int page = FIRST_PAGE + 1; page <= pagesToRead; page++) {
            Duration remaining = Duration.between(LocalDateTime.now(SERVICE_ZONE), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                // 남은 페이지를 못 읽었으니 온전한 회차가 아니다 — 아래 정리를 건너뛰게 실패로 센다.
                failedPages++;
                log.warn("축제 풀 조회가 전체 상한({})을 넘겨 {}페이지에서 멈춥니다", TOTAL_DEADLINE, page);
                break;
            }
            try {
                collected.addAll(festivalStandardClient.findAll(page, ROWS_PER_PAGE, remaining).items());
            } catch (RuntimeException e) {
                // 한 페이지가 깨져도 받은 것은 저장한다 — 전부 버리면 이번 달 내내 축제를 모른다.
                failedPages++;
                log.warn("축제 풀 페이지 조회 실패 page={} cause={}", page, RootCause.label(e));
            }
        }

        return save(collected, regionIdBySigungu, fetchedAt, totalPages, pagesToRead, failedPages);
    }

    /**
     * 받은 것을 우리 지역에 붙여 저장한다.
     *
     * <p><b>못 붙인 것을 센다.</b> 전국 1,305건 중 우리 89곳 밖이 대부분이라 그 자체는 정상이지만,
     * 붙은 것이 0이면 지역명 매칭이 깨졌다는 신호다.
     */
    private RefreshOutcome save(List<StandardFestival> collected, Map<String, Long> regionIdBySigungu,
            LocalDateTime fetchedAt, int totalPages, int pagesToRead, int failedPages) {
        boolean complete = totalPages <= pagesToRead && failedPages == 0;
        List<FestivalPlace> ours = new ArrayList<>();
        int unusable = 0;
        for (StandardFestival festival : collected) {
            Long regionId = regionIdBySigungu.get(festival.sigunguName());
            if (regionId == null) {
                continue; // 우리 89곳 밖 — 대부분이 여기다
            }
            if (!festival.isUsable()) {
                unusable++;
                continue;
            }
            ours.add(festival.toPlace(regionId, fetchedAt));
        }

        if (ours.isEmpty()) {
            // 빈 결과를 성공으로 남기지 않는다 — 다음 회차에 다시 받게 한다.
            log.warn("축제 풀 — 받은 {}건 중 우리 지역에 붙은 것이 없습니다. 지역명 매칭을 확인하세요",
                    collected.size());
            return RefreshOutcome.nothing();
        }

        int saved = festivalPlaceRepository.upsertAll(ours);
        int removed = removeCancelled(fetchedAt, complete);
        log.info("축제 풀 저장 완료 받은건수={} 우리지역={}건 저장={}건 좌표·기간없음={}건 취소정리={}건 온전={}",
                collected.size(), ours.size(), saved, unusable, removed, complete);
        return new RefreshOutcome(saved, complete);
    }

    /**
     * 이번에 안 온 축제를 지운다 — <b>온전히 훑은 회차에서만</b>.
     *
     * <p>페이지 상한에 걸렸거나 한 페이지라도 실패했으면 "이번에 안 온 것 = 취소됨" 이 성립하지 않는다.
     * 그때 지우면 멀쩡한 축제를 우리가 없앤다 — 조용히, 되돌릴 수 없게.
     */
    private int removeCancelled(LocalDateTime fetchedAt, boolean complete) {
        if (!complete) {
            log.info("이번 회차는 온전하지 않아 취소 정리를 건너뜁니다");
            return 0;
        }
        int removed = festivalPlaceRepository.deleteFetchedBefore(fetchedAt);
        if (removed > 0) {
            // 지운 것은 반드시 남긴다 — 축제가 화면에서 사라진 이유를 나중에 설명할 수 있어야 한다.
            log.info("이번 조회에 없어 취소로 보고 지웠습니다 {}건", removed);
        }
        return removed;
    }

    /**
     * 시군구명 → 우리 지역 id.
     *
     * <p><b>같은 이름이 전국에 여럿이다</b>(동구 6곳·중구 6곳). 그런데 우리 89곳 안에서는 시군구명이
     * 겹치지 않는 한 이 매칭이 맞다 — 겹치면 나중 것이 앞의 것을 덮으므로 그 사실을 로그로 남긴다.
     */
    private Map<String, Long> regionIdsBySigungu() {
        Map<String, Long> byName = new HashMap<>();
        for (Region region : regionQuery.all()) {
            Long previous = byName.put(region.getSigungu(), region.getId());
            if (previous != null) {
                log.warn("우리 89곳 안에 같은 시군구명이 둘입니다 — 축제가 한쪽에만 붙습니다 name={} regionId={},{}",
                        region.getSigungu(), previous, region.getId());
            }
        }
        return byName;
    }
}
