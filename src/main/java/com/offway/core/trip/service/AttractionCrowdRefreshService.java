package com.offway.core.trip.service;

import com.offway.core.common.batch.domain.ManualBatch;
import com.offway.core.common.batch.service.RunningBatches;
import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionQuery;
import com.offway.core.trip.domain.AttractionCrowdForecast;
import com.offway.core.trip.infrastructure.crowd.AttractionCrowdClient;
import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import com.offway.core.trip.repository.AttractionCrowdForecastRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 관광지 집중률 예측을 받아 혼잡 칩의 재료를 채운다(#565).
 *
 * <h2>지역당 1콜 — 순환이 필요 없다</h2>
 *
 * <p>이슈는 처음에 "89곳 × 30일 = 2,670건인데 일 1,000건" 이라 보고 3일 지역 순환을 잡았다. 그런데
 * <b>한도는 행이 아니라 요청 수</b>다. {@code numOfRows} 를 키우면 지역 하나가 한 요청에 오므로
 * 회차당 <b>89콜</b>이고 한도의 9% 다. 나눌 이유가 없고, 매일 돌면 예보 첫날이 늘 최신이다.
 *
 * <h2>지역마다 갈아 끼운다</h2>
 *
 * <p>지역별로 호출이 갈리므로 실패도 지역별로 갈린다. "이번 회차에 안 온 것" 을 전역으로 지우면
 * <b>호출이 실패한 지역의 예보까지 지운다</b>. 그래서 받은 지역만 통째로 갈아 끼우고, 못 받은 지역은
 * 옛 값을 그대로 둔다 — 예보는 날짜가 붙어 있어 옛 값도 그 날짜까지는 유효하다.
 *
 * <p>표가 자라지 않게 하는 것은 <b>지나간 날짜 쓸기</b>가 맡는다. "이미 지난 날" 은 어느 지역이
 * 실패했든 결과가 같은 판정이라 언제나 안전하다.
 *
 * <h2>16곳은 예보가 없다</h2>
 *
 * <p>커버리지가 73/89 다. 없는 지역은 {@code RegionVisitMetricsService} 의 요일계수로 폴백한다 —
 * 그건 외부 호출이 0이고 89곳 전부 표본을 충족한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionCrowdRefreshService implements ManualBatch {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매일 새벽 5시 10분.
     *
     * <p>다른 배치와 시각을 벌렸다 — 지역 장소 풀이 매월 1일 04:00, 축제 기간이 화요일 04:20,
     * 축제 풀이 매월 6일 04:50, 야영장이 매월 8일 04:40 이다. 겹치면 새벽에 외부 호출이 몰린다.
     */
    private static final String DAILY_AT_DAWN = "0 10 5 * * *";

    /**
     * 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 재배포하면 주기가 처음부터 다시 센다(#226·#231). 아래 선점이 그것을 막는다.
     */
    private static final String BOOT_CHECK_DELAY = "PT420S";

    private static final String BOOT_CHECK_INTERVAL = "PT6H";

    /** 이 주기 안에 이미 돌았으면 건너뛴다 — 재배포가 89콜을 다시 태우지 않게. */
    private static final Duration RUN_INTERVAL = Duration.ofHours(20);

    private static final String BATCH_NAME = "attraction-crowd-refresh";

    private static final Caller CALLER = Caller.of("집중률배치");

    /**
     * 회차 <b>전체</b>의 시간 상한.
     *
     * <p>호출 하나의 상한(30초)이 89번 곱해지면 최악 45분이다. 전체 상한을 따로 두고, 남은 예산을
     * 호출 하나까지 내려보낸다 — 그러지 않으면 마감 직전에 시작한 호출이 자기 상한만큼 더 기다린다
     * (CLAUDE.md 성능 규약).
     */
    private static final Duration TOTAL_DEADLINE = Duration.ofMinutes(15);

    /** 남은 예산이 이보다 적으면 다음 지역을 시작하지 않는다. */
    private static final Duration MIN_CALL_BUDGET = Duration.ofSeconds(2);

    private final AttractionCrowdClient attractionCrowdClient;
    private final RunningBatches runningBatches;
    private final AttractionCrowdForecastRepository forecastRepository;
    private final RegionQuery regionQuery;
    private final BatchRunRepository batchRunRepository;

    /** 배치를 멈추거나 한도 상한을 거는 스위치(#403). */
    private final ExternalApiBatchPolicy batchPolicy;

    @Override
    public String batchName() {
        return BATCH_NAME;
    }

    @Override
    public boolean runNow() {
        // 스케줄러도 이 메서드를 지난다 — 두 경로가 같은 표식을 잡아야 겹치지 않는다(#540).
        return runningBatches.runExclusively(BATCH_NAME, this::refreshIfStale);
    }

    @Scheduled(cron = DAILY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void scheduled() {
        // **수동 실행과 같은 선점을 지난다**(#540). 예전에는 여기서 아래를 곧장 불러,
        // 스케줄러가 도는 중에 관리자가 누르면 둘이 함께 돌았다.
        runNow();
    }

    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.TATS_CROWD_RATE)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "혼잡 칩이 왜 안 뜨지" 가 된다.
                log.info("집중률 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            // **선점으로 판정과 기록을 한 문장에 묶는다.** 트리거가 셋이라(cron · 부팅 확인 · 관리자
            // 수동) hasRunSince + markStarted 사이에 다른 실행이 끼어들면 89콜을 두 번 쏜다(#540).
            if (!batchRunRepository.tryStartSince(BATCH_NAME, now.minus(RUN_INTERVAL), now)) {
                log.info("집중률을 최근 {}시간 안에 이미 받아 갱신을 건너뜁니다", RUN_INTERVAL.toHours());
                return;
            }
            refresh();
        });
    }

    /**
     * 한 회차의 결과.
     *
     * @param regionsUpdated 갈아 끼운 지역 수
     * @param regionsEmpty 응답이 비어 건너뛴 지역 수 — 예보가 없는 지역이다(실측 16곳)
     * @param regionsFailed 호출·파싱이 실패해 옛 값을 유지한 지역 수
     * @param rowsSaved 저장한 행 수
     * @param sweptPastRows 지나간 날짜로 지운 행 수
     */
    public record RefreshOutcome(
            int regionsUpdated, int regionsEmpty, int regionsFailed, int rowsSaved, int sweptPastRows) {
    }

    /** 89곳을 돌며 지역마다 갈아 끼운다. 외부 호출은 트랜잭션 밖이고, 영속화만 리포지토리가 묶는다. */
    public RefreshOutcome refresh() {
        return refresh(LocalDate.now(SERVICE_ZONE));
    }

    /** 오늘을 밖에서 넣을 수 있게 연다 — 테스트가 날짜를 고정한다. */
    public RefreshOutcome refresh(LocalDate today) {
        List<Region> regions = regionQuery.all();
        if (regions.isEmpty()) {
            log.info("집중률 — 지역 마스터가 비어 있어 건너뜁니다");
            return new RefreshOutcome(0, 0, 0, 0, 0);
        }

        Instant deadline = Instant.now().plus(TOTAL_DEADLINE);
        LocalDateTime fetchedAt = LocalDateTime.now(SERVICE_ZONE);
        int updated = 0;
        int empty = 0;
        int failed = 0;
        int rows = 0;

        for (Region region : regions) {
            Duration left = Duration.between(Instant.now(), deadline);
            if (left.compareTo(MIN_CALL_BUDGET) < 0) {
                // 남은 지역은 옛 값을 그대로 든다 — 다음 회차가 이어 받는다.
                failed += regions.size() - updated - empty - failed;
                log.warn("집중률 회차 시간 상한({}) 초과 — 남은 지역은 다음 회차로 넘깁니다", TOTAL_DEADLINE);
                break;
            }
            List<AttractionCrowd> received;
            try {
                received = attractionCrowdClient.findByRegion(region.getLegalCode(), left);
            } catch (RuntimeException e) {
                failed++;
                log.warn("집중률 지역 조회 실패 — 옛 값을 유지합니다 regionId={} cause={}",
                        region.getId(), RootCause.label(e));
                continue;
            }
            if (received.isEmpty()) {
                // 예보가 없는 지역이다(실측 16곳). 비우지 않는다 — 응답이 비어 온 것과 관광지가
                // 없어진 것은 구분되지 않는다(성능 규약 "빈 응답을 성공으로 캐시하지 않는다").
                empty++;
                continue;
            }
            List<AttractionCrowdForecast> forecasts =
                    toForecasts(received, region.getId(), today, fetchedAt);
            if (forecasts.isEmpty()) {
                empty++;
                continue;
            }
            rows += forecastRepository.replaceRegion(region.getId(), forecasts);
            updated++;
        }

        // 지나간 날짜는 어느 지역이 실패했든 지워도 된다 — 표가 날마다 커지는 것을 막는 유일한 자리다.
        int swept = forecastRepository.deleteBefore(today);

        log.info("집중률 갱신 지역={}곳 예보없음={}곳 실패={}곳 저장={}행 지난날짜정리={}행",
                updated, empty, failed, rows, swept);
        return new RefreshOutcome(updated, empty, failed, rows, swept);
    }

    /**
     * 외부 결과를 엔티티로 — <b>지난 날짜는 버린다</b>.
     *
     * <p>예보 창이 오늘부터 30일이지만 경계에서 어제 것이 섞여 올 수 있다. 넣어 봐야 바로 다음 쓸기에
     * 지워지고, 그 사이 코스가 어제 예보를 읽을 이유도 없다.
     */
    private List<AttractionCrowdForecast> toForecasts(
            List<AttractionCrowd> received, long regionId, LocalDate today, LocalDateTime fetchedAt) {
        List<AttractionCrowdForecast> forecasts = new ArrayList<>(received.size());
        for (AttractionCrowd crowd : received) {
            if (crowd.date().isBefore(today)) {
                continue;
            }
            forecasts.add(AttractionCrowdForecast.builder()
                    .regionId(regionId)
                    .attractionName(crowd.attractionName())
                    .baseDate(crowd.date())
                    .rate(crowd.rate())
                    .fetchedAt(fetchedAt)
                    .build());
        }
        return forecasts;
    }
}
