package com.offway.core.trip.service;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiBatchPolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.FestivalPeriod;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourFestival;
import com.offway.core.trip.infrastructure.tour.dto.TourFestivalResult;
import com.offway.core.trip.repository.FestivalPeriodRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 축제 기간을 받아 둔다(#388).
 *
 * <h2>왜 배치인가</h2>
 *
 * <p>요청 경로에서 부르면 코스 하나에 축제 후보 수만큼 호출이 나간다. 축제 기간은 <b>하루 사이에 바뀌지
 * 않는</b> 값이라 미리 받아 두는 것이 맞다.
 *
 * <h2>한도 — 첫 호출이 남은 비용을 확정한다</h2>
 *
 * <p>지역별로 부르면 회차마다 89번이다. <b>전국을 한 번에 받아 {@code contentId} 로 맞추면 페이지 수만큼</b>
 * 이면 되고, 첫 응답의 {@code totalCount} 가 그 페이지 수를 즉시 알려준다 — 끝까지 돌아 보고서야 아는
 * 것이 아니다.
 *
 * <p>그래도 <b>상한을 둔다</b>. 외부가 예상보다 많은 건수를 주는 날이 있고, 그때 조용히 다 받으면 하루
 * 한도가 사라진다. 상한에 걸리면 <b>무엇을 못 받았는지 로그로 남긴다</b> — 잘린 결과를 "다 받았다" 로
 * 읽지 않으려는 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalPeriodRefreshService {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /**
     * 매주 화요일 새벽 4시 20분.
     *
     * <p><b>주 1회면 충분하다.</b> 축제 일정은 하루 사이에 바뀌지 않는다. 매일 돌면 같은 값을 위해 한도를
     * 일곱 배로 쓴다.
     *
     * <p>다른 배치와 겹치지 않게 시각을 벌렸다 — 지역 장소 풀이 매월 1일 04:00 이라 같은 시각을 피했다.
     */
    private static final String WEEKLY_AT_DAWN = "0 20 4 * * TUE";

    /**
     * 부팅 뒤 확인 주기 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 프로세스가 살아 있는 동안의 간격이라 <b>재배포하면 처음부터 다시 센다</b>
     * (#226·#231). 그래서 이 트리거만으로는 배포마다 도는데, 아래 마커가 그것을 막는다.
     */
    private static final String BOOT_CHECK_DELAY = "PT180S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    /** 이 주기 안에 이미 돌았으면 건너뛴다 — 재배포가 한도를 다시 태우지 않게. */
    private static final Duration RUN_INTERVAL = Duration.ofDays(7);

    private static final String BATCH_NAME = "festival-period-refresh";

    private static final Caller CALLER = Caller.of("축제기간배치");

    /** 한 페이지에 받는 건수 — TourAPI 상한과 다른 배치(100)에 맞춘다. */
    private static final int ROWS_PER_PAGE = 100;

    /**
     * 한 회차 페이지 상한.
     *
     * <p>전국 축제가 <b>몇 건인지 아직 실측하지 못했다</b>(로컬에 키가 없다). 그래서 넉넉하되 한도를
     * 위협하지 않는 값으로 막아 둔다 — 10페이지면 1,000건이고 호출 10번이라, 관광정보 일일 한도(1,000)의
     * <b>1%</b> 다.
     *
     * <p>첫 실행 로그가 실제 {@code totalCount} 를 찍는다. 그 값을 보고 상한을 다시 정하고
     * {@code docs/external-api-inventory.md} 에 남긴다.
     */
    private static final int MAX_PAGES = 10;

    /** 첫 페이지 번호 — TourAPI 는 1부터다. */
    private static final int FIRST_PAGE = 1;

    /**
     * 오늘에서 얼마나 되돌아볼 것인가(#388).
     *
     * <p><b>오늘을 기준으로 부르면 진행 중인 축제가 통째로 빠진다.</b> {@code eventStartDate} 는 "이
     * 날짜 이후에 <b>시작</b>하는" 행사를 주므로, 지난주에 시작해 다음 주까지 하는 축제가 조회에 안 잡힌다.
     * 그러면 그 축제는 기간을 모르는 채로 남고 — 하필 <b>여행 중에 실제로 열리는</b> 축제가 그렇다.
     *
     * <p>3개월로 잡았다. 대부분의 지역 축제가 며칠에서 몇 주라 넉넉하고, 범위를 넓힐수록 받아 오는
     * 건수가 늘어 페이지 상한에 먼저 걸린다. 이보다 긴 축제는 여전히 빠지는데, 그건 상한 경고와 첫
     * 실행의 {@code totalCount} 를 보고 다시 정한다.
     */
    private static final Period LOOKBACK = Period.ofMonths(3);

    private final TourApiClient tourApiClient;
    private final FestivalPeriodRepository festivalPeriodRepository;
    private final BatchRunRepository batchRunRepository;

    /** 배치를 멈추거나 한도 상한을 거는 스위치(#403). */
    private final ExternalApiBatchPolicy batchPolicy;

    @Scheduled(cron = WEEKLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void refreshIfStale() {
        CallerContext.run(CALLER, () -> {
            if (!batchPolicy.batchMayCall(BATCH_NAME, ExternalApi.TOUR_API)) {
                // 조용히 넘기지 않는다 — 꺼 둔 줄 모르면 "축제 기간이 왜 안 채워지지" 가 된다.
                log.info("축제 기간 배치가 꺼져 있거나 배치 한도를 넘겨 건너뜁니다");
                return;
            }
            LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
            if (batchRunRepository.hasRunSince(BATCH_NAME, now.minus(RUN_INTERVAL))) {
                log.info("축제 기간을 최근 {}일 안에 이미 받아 갱신을 건너뜁니다", RUN_INTERVAL.toDays());
                return;
            }
            // **성공한 회차만 기록한다.** 부르기 전에 적으면 첫 페이지가 깨진 날에도 7일을 건너뛴다 —
            // 한 번의 일시적 실패로 축제 기간이 일주일 낡는다. 이 배치는 회차당 열 콜 남짓이라
            // 다시 시도하는 비용이 그 위험보다 훨씬 싸다(지역 장소 풀은 267콜이라 반대로 판단했다).
            if (refresh(LocalDate.now(SERVICE_ZONE).minus(LOOKBACK)) > 0) {
                batchRunRepository.markStarted(BATCH_NAME, now);
            }
        });
    }

    /**
     * {@code from} 이후에 시작하는 축제의 기간을 받아 저장한다.
     *
     * <p><b>이미 시작한 축제는 안 받는다.</b> {@code eventStartDate} 가 "이 날짜 이후 시작" 이라, 오늘을
     * 넘기면 진행 중인 축제가 빠진다 — 그래서 호출자가 넉넉히 과거를 넘길 수 있게 인자로 열어 뒀고,
     * 스케줄러는 오늘을 넘긴다. 진행 중인 축제를 놓치는 문제는 아래 첫 페이지 로그로 드러난다.
     *
     * @return 저장한 건수
     */
    public int refresh(LocalDate from) {
        TourFestivalResult first;
        try {
            first = tourApiClient.findFestivals(from, FIRST_PAGE, ROWS_PER_PAGE);
        } catch (RuntimeException e) {
            // 첫 페이지가 깨지면 이번 회차는 없던 일이다. 기존 값을 덮지 않으므로 화면은 그대로다.
            log.warn("축제 기간 첫 페이지 조회 실패 — 이번 회차를 건너뜁니다 cause={}", RootCause.label(e));
            return 0;
        }

        int totalPages = first.totalPages(ROWS_PER_PAGE);
        int pagesToRead = Math.min(totalPages, MAX_PAGES);
        // **이 줄이 실측이다.** 첫 호출 하나로 전체 건수와 남은 호출 수가 확정된다.
        log.info("축제 기간 조회 시작 from={} 전체={}건 전체페이지={} 읽을페이지={}",
                from, first.totalCount(), totalPages, pagesToRead);
        if (totalPages > MAX_PAGES) {
            // 조용히 자르지 않는다. 무엇을 못 받았는지 남겨야 상한을 다시 정할 수 있다.
            log.warn("축제가 상한보다 많습니다 — {}페이지 중 {}페이지만 받습니다(약 {}건 누락). 상한을 다시 보세요",
                    totalPages, MAX_PAGES, (totalPages - MAX_PAGES) * ROWS_PER_PAGE);
        }

        List<TourFestival> collected = new ArrayList<>(first.items());
        int failedPages = 0;
        for (int page = FIRST_PAGE + 1; page <= pagesToRead; page++) {
            try {
                collected.addAll(tourApiClient.findFestivals(from, page, ROWS_PER_PAGE).items());
            } catch (RuntimeException e) {
                // 한 페이지가 깨져도 받은 것은 저장한다 — 전부 버리면 이번 주 내내 기간을 모른다.
                failedPages++;
                log.warn("축제 기간 페이지 조회 실패 page={} cause={}", page, RootCause.label(e));
            }
        }

        LocalDateTime fetchedAt = LocalDateTime.now(SERVICE_ZONE);
        int saved = festivalPeriodRepository.upsertAll(collected.stream()
                .map(festival -> toEntity(festival, fetchedAt))
                .toList());
        int removed = removeCancelled(collected, from, pagesToRead, totalPages, failedPages);
        log.info("축제 기간 저장 완료 받은건수={} 저장={}건 취소정리={}건", collected.size(), saved, removed);
        return saved;
    }

    /**
     * 취소된 축제를 걷어낸다 — <b>온전히 훑은 회차에서만</b>(#388).
     *
     * <h2>왜 필요한가</h2>
     *
     * <p>TourAPI 가 취소된 축제를 더 이상 안 주면 upsert 만으로는 <b>옛 행이 그대로 남는다.</b> 저장된
     * 미래 기간에는 {@code isOpenOn} 이 계속 참이라, 열리지도 않는 축제를 코스에 넣게 된다.
     *
     * <h2>왜 조건이 붙나</h2>
     *
     * <p>페이지 상한에 걸렸거나 한 페이지라도 실패했으면 <b>"이번에 안 온 것 = 취소됨" 이 성립하지
     * 않는다.</b> 그때 지우면 멀쩡한 축제를 우리가 없앤다 — 조용히, 되돌릴 수 없게.
     *
     * <p>범위도 {@code from} 이후로 좁힌다. 이번 조회가 그 날짜부터 시작하는 축제만 봤으므로, 그보다
     * 앞서 끝난 옛 행까지 지우면 보지도 않은 것을 없다고 단정하는 셈이다.
     */
    private int removeCancelled(
            List<TourFestival> collected, LocalDate from, int pagesToRead, int totalPages, int failedPages) {
        if (totalPages > pagesToRead || failedPages > 0) {
            log.info("이번 회차는 온전하지 않아 취소 정리를 건너뜁니다 전체페이지={} 읽음={} 실패={}",
                    totalPages, pagesToRead, failedPages);
            return 0;
        }
        List<String> kept = collected.stream().map(TourFestival::contentId).toList();
        int removed = festivalPeriodRepository.deleteMissingFrom(kept, from);
        if (removed > 0) {
            // 지운 것은 반드시 남긴다 — 축제가 화면에서 사라진 이유를 나중에 설명할 수 있어야 한다.
            log.info("이번 조회에 없어 취소로 보고 지웠습니다 {}건", removed);
        }
        return removed;
    }

    private static FestivalPeriod toEntity(TourFestival festival, LocalDateTime fetchedAt) {
        return FestivalPeriod.builder()
                .contentId(festival.contentId())
                .eventStart(festival.eventStart())
                .eventEnd(festival.eventEnd())
                .title(festival.title())
                .fetchedAt(fetchedAt)
                .build();
    }
}
