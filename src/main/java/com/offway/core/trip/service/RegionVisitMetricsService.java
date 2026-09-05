package com.offway.core.trip.service;

import com.offway.core.trip.domain.PopularityTrend;
import com.offway.core.trip.domain.QuietestDay;
import com.offway.core.trip.domain.RegionDailyTourists;
import com.offway.core.trip.domain.RegionVisitMetrics;
import com.offway.core.trip.domain.VisitWindow;
import com.offway.core.trip.domain.WeeklyVisitPattern;
import com.offway.core.trip.repository.RegionVisitorDailyRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역별 방문 지표를 계산해 들고 있는다(#394) — 한산한 요일과 인기 추세.
 *
 * <h2>왜 캐시하나</h2>
 *
 * <p>재료가 <b>월 1회만</b> 바뀐다. 원본이 완결된 달만 발행하기 때문이다. 그런데 계산은 15개월 ×
 * 89곳을 훑으므로, 매 요청 다시 하면 목록 한 번에 4만 행을 접는다.
 *
 * <p>그래서 <b>가장 최근 받은 날이 바뀌었을 때만</b> 다시 계산한다. 그 확인은 인덱스 max 하나라 싸다.
 *
 * <h2>비어 있어도 화면은 나간다</h2>
 *
 * <p>적재 전이거나 표본이 모자라면 지표가 없다. 그때도 목록·상세는 그대로 나가고 그 줄만 빠진다 —
 * 지표는 <b>덤</b>이지 화면의 전제가 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionVisitMetricsService {

    /**
     * 요일 패턴 표본 기간 — 열두 달.
     *
     * <p>계절이 상쇄되려면 열두 달이 필요하다(#395). 석 달치로 낸 "토요일 계수" 는 사실 "여름 계수" 다.
     */
    private static final int PATTERN_MONTHS = 12;

    /**
     * 추세를 재는 구간 길이 — 석 달.
     *
     * <p>한 달이면 그 달에 축제가 하나 낀 것만으로 흔들리고, 여섯 달이면 "요즘" 이라 부르기 어렵다.
     */
    private static final int TREND_MONTHS = 3;

    /** 작년 같은 기간까지 거슬러야 계절이 상쇄된다 — 그래서 재료가 {@value} 개월 필요하다. */
    static final int REQUIRED_MONTHS = TREND_MONTHS + 12;

    private final RegionVisitorDailyRepository dailyRepository;

    /** 계산 중인가 — 동시 요청이 같은 집계를 겹쳐 돌리지 않게. */
    private final AtomicBoolean building = new AtomicBoolean();

    private volatile Snapshot snapshot = Snapshot.empty();

    /**
     * 그 지역의 지표. 아직 못 내는 지역이면 {@link RegionVisitMetrics#none()} 이다.
     *
     * @param signguCode 법정 시군구코드 — 지명이 아니다
     */
    public RegionVisitMetrics of(String signguCode) {
        return current().getOrDefault(signguCode, RegionVisitMetrics.none());
    }

    /** 코드 → 지표 전체. 목록처럼 여러 지역을 한 번에 그리는 화면이 쓴다. */
    public Map<String, RegionVisitMetrics> all() {
        return current();
    }

    /**
     * 지금 쓸 스냅샷 — 재료가 새로 들어왔으면 다시 계산한다.
     *
     * <p><b>기다리지 않는다.</b> 다른 스레드가 계산 중이면 들고 있던 것을 그대로 준다. 지표는 덤이라
     * 한 요청이 이 계산을 기다릴 이유가 없고, 다음 요청부터는 새 값이 나간다.
     */
    private Map<String, RegionVisitMetrics> current() {
        LocalDate latest = dailyRepository.latestDate().orElse(null);
        if (latest == null) {
            return Map.of(); // 아직 아무것도 적재되지 않았다.
        }
        Snapshot held = snapshot;
        if (latest.equals(held.builtFor())) {
            return held.byCode();
        }
        if (!building.compareAndSet(false, true)) {
            return held.byCode();
        }
        try {
            Snapshot rebuilt = build(latest);
            snapshot = rebuilt;
            log.info("지역 방문 지표 재계산 기준일={} 지역={}곳", latest, rebuilt.byCode().size());
            return rebuilt.byCode();
        } catch (RuntimeException e) {
            // 지표는 덤이다 — 계산이 터져도 목록·상세는 나가야 한다. 들고 있던 것으로 계속한다.
            log.warn("지역 방문 지표 재계산 실패 — 이전 값을 유지합니다", e);
            return held.byCode();
        } finally {
            building.set(false);
        }
    }

    /**
     * 최신 적재일 기준으로 전 지역 지표를 만든다.
     *
     * <p>기간은 <b>달 경계</b>로 자른다. 원본이 월 단위로 발행돼 마지막 달만 잘리면 그 지역의 요일
     * 표본이 한쪽으로 기운다.
     */
    private Snapshot build(LocalDate latest) {
        YearMonth latestMonth = YearMonth.from(latest);
        YearMonth patternFrom = latestMonth.minusMonths(PATTERN_MONTHS - 1L);
        YearMonth recentFrom = latestMonth.minusMonths(TREND_MONTHS - 1L);
        YearMonth baselineFrom = recentFrom.minusYears(1);
        YearMonth baselineTo = latestMonth.minusYears(1);

        // 패턴과 작년 기준선 중 더 이른 쪽부터 한 번에 읽는다 — 질의를 두 번 나눌 이유가 없다.
        YearMonth readFrom = baselineFrom.isBefore(patternFrom) ? baselineFrom : patternFrom;
        List<RegionDailyTourists> rows =
                dailyRepository.sumTouristsByDate(readFrom.atDay(1), latestMonth.atEndOfMonth());

        Map<String, RegionAccumulator> byCode = new HashMap<>();
        for (RegionDailyTourists row : rows) {
            byCode.computeIfAbsent(row.signguCode(), code -> new RegionAccumulator())
                    .add(row, patternFrom, recentFrom, latestMonth, baselineFrom, baselineTo);
        }

        Map<String, RegionVisitMetrics> metrics = new HashMap<>();
        byCode.forEach((code, acc) -> metrics.put(code, acc.toMetrics()));
        return new Snapshot(latest, Map.copyOf(metrics));
    }

    /** 한 지역의 누적 — 요일 패턴용 일자별 값과, 추세용 두 창. */
    private static final class RegionAccumulator {

        private final Map<LocalDate, Double> patternDays = new HashMap<>();
        private double recentSum;
        private int recentDays;
        private double baselineSum;
        private int baselineDays;

        private void add(RegionDailyTourists row, YearMonth patternFrom, YearMonth recentFrom,
                YearMonth latestMonth, YearMonth baselineFrom, YearMonth baselineTo) {
            YearMonth month = YearMonth.from(row.date());
            if (within(month, patternFrom, latestMonth)) {
                patternDays.put(row.date(), row.tourists());
            }
            if (within(month, recentFrom, latestMonth)) {
                recentSum += row.tourists();
                recentDays++;
            }
            if (within(month, baselineFrom, baselineTo)) {
                baselineSum += row.tourists();
                baselineDays++;
            }
        }

        private static boolean within(YearMonth month, YearMonth from, YearMonth to) {
            return !month.isBefore(from) && !month.isAfter(to);
        }

        private RegionVisitMetrics toMetrics() {
            QuietestDay quietest = WeeklyVisitPattern.of(patternDays)
                    .flatMap(WeeklyVisitPattern::quietest)
                    .orElse(null);
            PopularityTrend trend = PopularityTrend.of(
                            new VisitWindow(recentSum, recentDays),
                            new VisitWindow(baselineSum, baselineDays))
                    .orElse(null);
            return new RegionVisitMetrics(quietest, trend);
        }
    }

    /**
     * 계산 결과와 그 기준일.
     *
     * <p>기준일을 함께 든다 — 그래야 "새 달이 적재됐나" 를 값 하나 비교로 판정한다.
     */
    private record Snapshot(LocalDate builtFor, Map<String, RegionVisitMetrics> byCode) {

        private static Snapshot empty() {
            return new Snapshot(null, Map.of());
        }
    }
}
