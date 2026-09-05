package com.offway.core.trip.service;

import com.offway.core.common.external.Caller;
import com.offway.core.common.external.CallerContext;
import com.offway.core.common.logging.RootCause;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionVisitorDaily;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import com.offway.core.trip.repository.RegionVisitorDailyRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 지역 일별 방문자를 채운다(#394) — <b>혼잡도의 재료</b>.
 *
 * <h2>한 번 쌓으면 다시 안 쌓는다</h2>
 *
 * <p>원본은 <b>완결된 달만 월 단위로 발행</b>한다. 그래서 지난달 값은 영원히 안 바뀌고, 한 번 받으면
 * 다시 받을 이유가 없다.
 *
 * <p>그 성질을 그대로 가드로 쓴다 — <b>이미 받은 달은 외부를 부르지 않는다.</b> 재배포가 잦아도 같은
 * 답을 다시 받지 않는다(#226·#231 이 경계한 자리다).
 *
 * <h2>왜 랭킹 집계와 따로 도나</h2>
 *
 * <p>{@code RegionRankingService} 는 <b>매달 마지막 7일</b>만 본다. 그 표본으로는 요일 패턴을 못 낸다 —
 * 요일당 한 달에 하나뿐이다. 여기서는 <b>한 달 전체</b>를 받아 요일당 4~5개를 쌓는다.
 *
 * <p>그렇다고 랭킹 쪽 관측 창을 넓히지는 않는다. {@code observedDays} 가 베이지안 prior 에 들어가서,
 * 넓히면 <b>혼잡도를 고치려다 추천 순서가 조용히 바뀐다.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionVisitorDailyRefreshService {

    private static final String SERVICE_ZONE_ID = "Asia/Seoul";
    private static final ZoneId SERVICE_ZONE = ZoneId.of(SERVICE_ZONE_ID);

    /** 매월 5일 새벽 — 지난달이 발행되고도 며칠 지난 시점이라 미발행으로 헛돌 일이 적다. */
    private static final String MONTHLY_AT_DAWN = "0 40 4 5 * *";

    /**
     * 부팅 뒤 확인 — 배포가 잦아 cron 을 놓칠 수 있다.
     *
     * <p>{@code fixedDelay} 는 재배포하면 주기가 처음부터 다시 센다. 그래도 괜찮은 것은 <b>이미 받은
     * 달이면 외부를 안 부르기</b> 때문이다 — 이 배치는 마커가 아니라 <b>데이터 자체</b>가 가드다.
     */
    private static final String BOOT_CHECK_DELAY = "PT240S";

    private static final String BOOT_CHECK_INTERVAL = "P7D";

    private static final Caller CALLER = Caller.of("지역방문자일별배치");

    /**
     * 채울 개월 수 — <b>지표가 요구하는 만큼</b>이다.
     *
     * <p>숫자를 여기에 따로 적지 않는다. 인기 추세가 작년 같은 기간과 견주므로(계절 상쇄) 필요한
     * 길이는 지표 쪽이 안다 — 양쪽에 적으면 한쪽만 바뀐다.
     *
     * <p>첫 실행만 비싸고(월 5콜 × 15 ≈ 75콜, 관광빅데이터 한도 1,000 의 <b>7.5%</b>) 그 뒤로는 새로
     * 발행된 달만 받는다.
     */
    private static final int BACKFILL_MONTHS = RegionVisitMetricsService.REQUIRED_MONTHS;

    /**
     * 페이지 크기 — <b>응답이 {@code maxInMemorySize}(2MB)를 넘지 않는 선</b>.
     *
     * <p>실측 근거는 {@code TourDataLabClientImpl} 에 있다: 관측 창 7일이 5,628건에 약 1MB 였다.
     * <b>건당 약 186바이트</b>다. 그래서 10,000건이면 1.86MB 로 한도까지 여유가 7% 밖에 없다 —
     * 시군구명이 긴 달이나 원본 필드가 하나 늘면 그대로 넘긴다.
     *
     * <p>넘으면 그 달이 통째로 버려지고(부분 적재 금지), 다음 회차에 다시 같은 크기로 물어 <b>영영
     * 안 채워진다</b>. 조용히 굳는 실패라, 콜 몇 건을 아끼자고 감수할 값이 아니다.
     *
     * <p>{@value} 건이면 약 0.9MB 로 절반이다. 한 달 전국이 약 24,000행이라 다섯 페이지가 된다.
     */
    private static final int PAGE_SIZE = 5_000;

    private static final int FIRST_PAGE = 1;

    /**
     * 한 달의 페이지 상한 — 폭주 안전장치.
     *
     * <p>{@code totalCount} 가 잘못 크거나 페이지가 안 줄어도 무한 루프에 빠지지 않게 막는다. 한 달이
     * 다섯 페이지라 여덟이면 여유가 있고, 넘으면 원본이 이상하다는 신호라 로그로 남긴다.
     *
     * <p><b>딱 맞게 잡지 않는다.</b> 상한과 실제가 같으면 원본이 조금만 늘어도 매달 상한에 걸려 그 달을
     * 버린다.
     */
    private static final int MAX_PAGES_PER_MONTH = 8;

    /** 호출 하나를 기다릴 상한. 집계가 아니라 배치라 넉넉히 준다 — 사용자가 기다리는 경로가 아니다. */
    private static final Duration CALL_BUDGET = Duration.ofSeconds(20);

    private final TourDataLabClient tourDataLabClient;
    private final RegionVisitorDailyRepository dailyRepository;
    private final RegionRepository regionRepository;

    @Scheduled(cron = MONTHLY_AT_DAWN, zone = SERVICE_ZONE_ID)
    @Scheduled(initialDelayString = BOOT_CHECK_DELAY, fixedDelayString = BOOT_CHECK_INTERVAL)
    public void backfillIfMissing() {
        CallerContext.run(CALLER, () -> backfill(YearMonth.from(LocalDate.now(SERVICE_ZONE))));
    }

    /**
     * {@code current} 직전 달부터 거슬러 {@value #BACKFILL_MONTHS}개월을 채운다.
     *
     * <p><b>이미 있는 달은 건너뛴다 — 외부를 부르지도 않는다.</b> 그래서 두 번째 실행부터는 새로 발행된
     * 한 달만 받고, 아무것도 발행 안 됐으면 호출이 0건이다.
     *
     * @return 이 실행으로 새로 넣은 행 수
     */
    public int backfill(YearMonth current) {
        Set<String> ourCodes = ourRegionCodes();
        if (ourCodes.isEmpty()) {
            // 지역 마스터가 아직 안 올라왔다. 지금 받아 봐야 전부 걸러져 버려진다.
            log.info("지역 방문자 일별 — 지역 마스터가 비어 있어 건너뜁니다");
            return 0;
        }

        int inserted = 0;
        int fetchedMonths = 0;
        for (int back = 1; back <= BACKFILL_MONTHS; back++) {
            YearMonth month = current.minusMonths(back);
            if (dailyRepository.hasMonth(month)) {
                continue; // 값이 불변이라 이미 있으면 옳다. 외부를 부르지 않는다.
            }
            int saved = fetchMonth(month, ourCodes);
            if (saved > 0) {
                fetchedMonths++;
                inserted += saved;
            }
        }
        if (fetchedMonths > 0) {
            log.info("지역 방문자 일별 적재 완료 받은달={}개 저장={}행", fetchedMonths, inserted);
        }
        return inserted;
    }

    /**
     * 한 달을 통째로 받아 <b>우리 89곳만</b> 남긴다.
     *
     * <p>원본은 전국 229곳을 준다. 걸러 저장하면 적재량이 2.6배 줄고, 안 쓰는 지역이 표를 채우지 않는다.
     *
     * <p>한 페이지라도 실패하면 <b>그 달 전체를 버린다.</b> 부분 적재하면 그 달의 요일 평균이 빠진
     * 날만큼 왜곡되는데, 나중에 "이미 있는 달" 로 판정돼 <b>영영 안 채워진다</b> — 조용히 틀린 값이
     * 굳는 자리다.
     */
    private int fetchMonth(YearMonth month, Set<String> ourCodes) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<RegionVisitorDaily> collected = new ArrayList<>();
        int fetched = 0;
        for (int page = FIRST_PAGE; page < FIRST_PAGE + MAX_PAGES_PER_MONTH; page++) {
            TourVisitorResult result;
            try {
                result = tourDataLabClient.findRegionVisitors(from, to, page, PAGE_SIZE, CALL_BUDGET);
            } catch (RuntimeException e) {
                log.warn("지역 방문자 일별 조회 실패 — {} 를 통째로 건너뜁니다 page={} cause={}",
                        month, page, RootCause.label(e));
                return 0;
            }
            result.items().stream()
                    .filter(visitor -> ourCodes.contains(visitor.signguCode()))
                    .map(RegionVisitorDailyRefreshService::toEntity)
                    .forEach(collected::add);
            fetched += result.items().size();
            if (result.items().isEmpty() || fetched >= result.totalCount()) {
                break;
            }
            if (page == FIRST_PAGE + MAX_PAGES_PER_MONTH - 1) {
                // 상한에 걸렸다 = 부분 수집이다. 위 주석대로 버린다.
                log.warn("지역 방문자 일별 — {} 이 페이지 상한을 넘겨({}/{}건) 그 달을 버립니다",
                        month, fetched, result.totalCount());
                return 0;
            }
        }

        if (collected.isEmpty()) {
            // 빈 응답을 성공으로 남기지 않는다 — 아직 미발행이면 다음 회차에 다시 묻는다.
            log.info("지역 방문자 일별 — {} 은 아직 발행되지 않았습니다(빈 결과)", month);
            return 0;
        }
        int saved = dailyRepository.insertIfAbsent(collected);
        log.info("지역 방문자 일별 {} 저장 지역={}곳 행={}", month, ourCodes.size(), saved);
        return saved;
    }

    /** 우리가 쓰는 89곳의 법정 시군구코드. 지명이 아니라 코드로 맞춘다 — 같은 이름이 전국에 여럿이다. */
    private Set<String> ourRegionCodes() {
        return regionRepository.findAll().stream()
                .map(region -> region.getLegalCode())
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static RegionVisitorDaily toEntity(RegionVisitor visitor) {
        return RegionVisitorDaily.builder()
                .signguCode(visitor.signguCode())
                .baseDate(visitor.baseDate())
                .visitorType(visitor.type())
                .visitorCount(visitor.count())
                .build();
    }
}
