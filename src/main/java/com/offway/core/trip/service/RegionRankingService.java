package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.trip.domain.PopulationDeclineStatus;
import com.offway.core.trip.domain.RegionRanking;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.domain.RegionVisitorAggregate;
import com.offway.core.trip.domain.RegionVisitorStat;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.repository.RegionVisitorAggregateRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역을 방문자 데이터로 랭킹한다 — 추천(#23)과 홈(#22)이 공유한다. 관광빅데이터를 <b>법정 시군구코드</b>로
 * 집계(관광객=외지인+외국인)해 {@link RegionRanking}(베이지안 보정)에 넘긴다.
 *
 * <p><b>지명으로 집계하면 안 된다.</b> 전국에 동구 6곳·중구 6곳·서구 5곳·남구 4곳·북구 4곳·고성군 2곳이 있어
 * 서로 다른 지역의 방문자가 한 버킷에 합산된다(예: 부산 동구가 전국 동구 6곳의 합을 받아 6배 부풀려짐). 우리 89곳
 * 중 부산 동구·부산 서구·대구 남구·대구 서구·강원 고성군·경남 고성군이 여기 걸린다.
 *
 * <p>생활인구는 <b>랭킹 가중치</b>일 뿐이라 조회 실패로 홈·추천을 막지 않는다(키 없음과 동일 취급 — 방문자 0 폴백). 또 완결된 달만
 * 월 단위로 발행돼 매 요청 재조회할 이유가 없어 인메모리로 캐시하고, 실패 시엔 마지막 성공값을 재사용(stale-while-error)해 느린
 * 외부에 요청이 몰리는 것을 막는다. 외부 호출만 하고 DB 는 건드리지 않으므로 트랜잭션으로 감싸지 않는다(6초 외부 대기 동안 커넥션을
 * 잡지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionRankingService {

    /**
     * 관측 창 길이 — 발행된 달의 <b>마지막 한 주</b>. 한 주면 평일·주말이 한 번씩 다 들어오고 한 페이지로 끝난다
     * (실호출: 7일 = 5,628건).
     */
    private static final int OBSERVE_SPAN_DAYS = 7;
    /**
     * 미발행 월을 만나면 이전 달로 물러서는 횟수. 관광빅데이터는 <b>완결된 달만 월 단위로</b> 발행된다(실호출 확인:
     * 2026-07-30 기준 6월까지 있고 7월은 0건). 고정 일수 지연을 가정하면 창이 미발행 구간에 걸려 조회가
     * {@code resultCode=0000} + 빈 결과로 <b>조용히</b> 성공하고, 전 지역 방문자가 0이 돼 랭킹이 무의미해진다.
     */
    private static final int MAX_MONTHS_BACK = 3;
    /**
     * 집계 <b>전체</b>의 시간 상한. 클라이언트의 timeout(20초)은 HTTP 요청 <b>한 건</b>에 걸리므로, 페이지·월을 순차로
     * 도는 이 집계에서는 상한이 곱해진다 — {@value #MAX_MONTHS_BACK}개월 × {@value #MAX_PAGES}페이지 × 20초면
     * 최악 3,000초다. single-flight 라 한 스레드만 이 대기를 타지만, 그 한 스레드가 요청 스레드일 수 있어 상한이 필요하다.
     *
     * <p>실제로는 관측 창 7일이 한 페이지(5,628건)로 끝나 1회 호출이면 되고, 미발행 월은 빈 결과로 즉시 돌아온다. 이 값은
     * 정상 경로의 여유가 아니라 <b>병리적 누적을 끊는 상한</b>이다(예: 외부가 잘못된 totalCount 를 계속 돌려줄 때).
     *
     * <p>이 상한은 호출 <b>직전 검사만으로는 지켜지지 않는다</b> — 예산이 1초 남은 시점에 시작한 요청이 클라이언트
     * timeout(20초)만큼 더 대기하면 실제 상한이 80초가 된다. 그래서 남은 예산을 클라이언트에 함께 넘겨(min 적용)
     * 진행 중인 마지막 요청도 이 시점에 끊기게 한다.
     */
    private static final Duration AGGREGATE_DEADLINE = Duration.ofSeconds(60);
    /**
     * 남은 예산이 이보다 적으면 페이지를 <b>시작하지 않는다</b>. 0 에 가까운 예산으로 호출하면 클라이언트가 곧바로
     * timeout 을 던져 502 로 올라간다 — 시간이 없어서 못 모은 것을 외부 장애로 보고하는 셈이라, 그 전에
     * "상한 초과 → 빈 결과" 경로로 정상 종료시킨다.
     */
    private static final Duration MIN_CALL_BUDGET = Duration.ofSeconds(1);
    private static final int FIRST_PAGE_NUMBER = 1;
    private static final int VISITOR_PAGE_SIZE = 10_000;
    /** 페이지 폭주 안전장치 — total 이 잘못 크거나 페이지가 안 줄어도 무한 루프에 빠지지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 50;
    private final TourDataLabClient tourDataLabClient;
    private final RegionVisitorAggregateRepository aggregateRepository;

    /** 최초 적재가 진행 중인가 — 동시 요청이 같은 60초 집계를 겹쳐 돌리지 않게. */
    private final java.util.concurrent.atomic.AtomicBoolean bootstrapping =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** 저장된 집계를 비운다 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트의 격리용. */
    public void evictCache() {
        aggregateRepository.replaceAll(List.of());
    }

    /**
     * 관광빅데이터를 다시 받아 <b>영속</b>한다 — 워머가 부른다(#193).
     *
     * <p>비어 있으면 저장하지 않는다. 빈 집계로 덮으면 전 지역 방문자가 0이 돼 랭킹이 무의미해지는데,
     * 그건 이전 값을 그대로 두는 것보다 나쁘다 — 미발행·장애가 지나가면 이전 달 값으로도 순위는 선다.
     *
     * <p>외부 호출은 트랜잭션 밖에서 끝난다. 영속화만 리포지토리(별도 빈)에 위임해 짧은 트랜잭션으로 묶는다.
     */
    public void refresh() {
        Aggregated aggregated;
        try {
            aggregated = fetchTourists();
        } catch (TourApiException e) {
            // 방문자 집계는 랭킹 <b>가중치</b>일 뿐이라 조회 실패로 홈·추천을 막지 않는다. 저장된 이전 값이 있으면
            // 그대로 쓰이고, 없으면 방문자 0 폴백으로 순위가 선다 — 어느 쪽이든 502 로 죽는 것보다 낫다.
            log.warn("관광빅데이터 갱신 실패 — 이전 집계를 유지합니다 cause={}", e.getClass().getSimpleName());
            return;
        }
        if (aggregated.byCode().isEmpty()) {
            log.warn("관광빅데이터 갱신 결과가 비어 저장을 건너뜁니다 — 이전 집계를 유지합니다(저장={}건)",
                    aggregateRepository.count());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RegionVisitorAggregate> rows = aggregated.byCode().entrySet().stream()
                .map(entry -> RegionVisitorAggregate.of(
                        entry.getKey(), aggregated.month(), entry.getValue().total, entry.getValue().observedDays(), now))
                .toList();
        aggregateRepository.replaceAll(rows);
        log.info("관광빅데이터 갱신 완료 baseYm={} 지역={}건", aggregated.month(), rows.size());
    }

    /**
     * 저장된 집계가 이미 <b>가장 최근 발행분</b>인가 — 그렇다면 외부를 부르지 않는다.
     *
     * <p>원본은 완결된 달만 월 단위로 발행되므로 지난달 것을 갖고 있으면 더 새 것은 존재하지 않는다.
     * 예전에는 6시간 TTL 로 하루 네 번, 배포마다 또 물어 같은 답을 반복 확인했다.
     */
    public boolean hasLatest() {
        YearMonth newestPossible = YearMonth.from(LocalDate.now()).minusMonths(1);
        return aggregateRepository.latestBaseMonth()
                .filter(baseMonth -> !baseMonth.isBefore(newestPossible))
                .isPresent();
    }

    /**
     * 저장된 집계를 랭킹이 쓰는 모양으로 되돌린다. 평상시엔 <b>DB 만 읽는다</b>.
     *
     * <p><b>비어 있을 때만</b> 한 번 받아 온다 — 새 환경에 처음 배포해 워머가 아직 안 돈 구간이다. 그마저 하지
     * 않으면 전 지역 방문자가 0이 돼 랭킹이 무의미해진다.
     *
     * <p>동시 요청은 <b>기다리지 않는다</b>. 이 적재는 호출 하나가 아니라 {@link #AGGREGATE_DEADLINE} 짜리
     * 집계라, 기다려 봐야 대부분 상한에 걸려 결국 같은 결과에 지연만 얹힌다. 첫 요청 하나만 채우고 나머지는
     * 빈 가중치로 지나간다 — 다음 요청부터는 DB 에 있다.
     *
     * <p><b>적재가 실패해도 랭킹은 나간다.</b> {@link #refresh()} 는 {@link TourApiException} 만 삼키므로
     * 영속화 충돌·락 타임아웃 같은 {@link RuntimeException} 은 여기까지 올라온다. 이 경로는 <b>요청 스레드</b>라
     * (워머와 달리) 위에 안전망이 없어 그대로 두면 500 이 된다 — 가중치일 뿐인 값 때문에 홈·추천이 죽는 것은
     * 이 클래스의 설계 의도와 정반대다. 빈 가중치로도 순위는 선다.
     */
    private Map<String, VisitorAgg> stored() {
        Map<String, VisitorAgg> byCode = read();
        if (byCode.isEmpty() && bootstrapping.compareAndSet(false, true)) {
            try {
                refresh();
            } catch (RuntimeException e) {
                log.warn("방문자 집계 최초 적재 실패 — 빈 가중치로 진행합니다", e);
                return byCode;
            } finally {
                bootstrapping.set(false);
            }
            return read();
        }
        return byCode;
    }

    private Map<String, VisitorAgg> read() {
        Map<String, VisitorAgg> byCode = new HashMap<>();
        for (RegionVisitorAggregate row : aggregateRepository.findAll()) {
            byCode.put(row.getSignguCode(), VisitorAgg.of(row.getVisitorTotal(), row.getObservedDays()));
        }
        return byCode;
    }

    /** 집계 결과와 그 기준 달 — 어느 달 것인지 함께 저장해야 갱신 필요 여부를 판단할 수 있다. */
    private record Aggregated(Map<String, VisitorAgg> byCode, YearMonth month) {
    }

    /** 주어진 지역들을 방문자 랭킹(점수 내림차순)으로 돌려준다. 키 없으면 방문자 0으로 랭킹(로컬 실행성). */
    public List<RegionScore> rankByVisitors(List<Region> regions) {
        if (regions.isEmpty()) {
            return List.of();
        }
        Map<String, VisitorAgg> visitorsByCode = stored();
        List<RegionVisitorStat> stats = regions.stream().map(region -> statOf(region, visitorsByCode)).toList();
        return RegionRanking.rank(stats);
    }

    /**
     * 가장 최근 <b>발행된</b> 달의 관측 창을 집계한다. 지난달부터 시작해 빈 결과면 이전 달로 물러선다 — 발행이 한두 달 밀려도
     * 랭킹이 0 가중치로 죽지 않게. 끝까지 비면 warn 을 남긴다(빈 결과는 예외가 아니라 정상 응답으로 와서, 로그가 없으면
     * 랭킹이 무의미해진 것을 아무도 모른다).
     */
    private Aggregated fetchTourists() {
        Instant deadline = Instant.now().plus(AGGREGATE_DEADLINE);
        YearMonth month = YearMonth.from(LocalDate.now()).minusMonths(1);
        for (int back = 0; back < MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            Map<String, VisitorAgg> byCode = fetchMonthTail(month, deadline);
            if (!byCode.isEmpty()) {
                return new Aggregated(byCode, month);
            }
            if (budgetExhausted(deadline)) {
                // 빈 결과의 원인이 미발행이 아니라 시간 상한이다 — 이전 달로 물러서도 같은 상한에 걸린다.
                log.warn("관광빅데이터 집계 시간 상한({}) 초과 — 갱신을 건너뜁니다", AGGREGATE_DEADLINE);
                return new Aggregated(Map.of(), month);
            }
            log.info("관광빅데이터 {} 미발행(빈 결과) — 이전 달로 물러섭니다", month);
        }
        log.warn("관광빅데이터 최근 {}개월이 모두 비었습니다 — 갱신을 건너뜁니다", MAX_MONTHS_BACK);
        return new Aggregated(Map.of(), month);
    }

    private static Duration remaining(Instant deadline) {
        return Duration.between(Instant.now(), deadline);
    }

    /** 남은 예산이 한 건을 시작할 만큼도 안 되면 소진으로 본다. */
    private static boolean budgetExhausted(Instant deadline) {
        return remaining(deadline).compareTo(MIN_CALL_BUDGET) < 0;
    }

    /**
     * 한 달의 관측 창(마지막 {@value #OBSERVE_SPAN_DAYS}일)을 <b>전 페이지</b> 모아 법정 시군구코드로 집계한다(관광객
     * 합·관측 일수). 부분 페이지로 랭킹하면 뒷페이지 지역이 0으로 과소집계돼 순위가 틀어지므로, totalCount 까지 페이지를
     * 끝까지 읽는다. 키 없으면 빈 결과(로컬 실행성).
     *
     * <p>{@code deadline} 을 넘기면 <b>모아둔 부분 결과를 버리고</b> 빈 결과를 준다. 같은 이유다 — 부분 데이터로
     * 랭킹하는 것은 랭킹을 포기하는 것보다 나쁘다(뒷페이지 지역이 0으로 과소집계돼 순위가 조용히 틀어진다).
     */
    private Map<String, VisitorAgg> fetchMonthTail(YearMonth month, Instant deadline) {
        LocalDate to = month.atEndOfMonth();
        LocalDate from = to.minusDays(OBSERVE_SPAN_DAYS - 1);

        Map<String, VisitorAgg> byCode = new HashMap<>();
        int fetched = 0;
        int total = 0;
        for (int page = FIRST_PAGE_NUMBER; page < FIRST_PAGE_NUMBER + MAX_PAGES; page++) {
            Duration budget = remaining(deadline);
            if (budget.compareTo(MIN_CALL_BUDGET) < 0) {
                log.warn("관광빅데이터 집계 시간 상한 초과 — {} 부분 수집({}/{}건)을 버립니다(부분 랭킹 금지)",
                        month, fetched, total);
                return Map.of();
            }
            // 남은 예산을 함께 넘긴다 — 클라이언트가 자기 timeout 과 이 값 중 짧은 쪽만 기다려, 이 한 건이
            // deadline 을 넘겨 대기하지 않는다.
            var result = tourDataLabClient.findRegionVisitors(from, to, page, VISITOR_PAGE_SIZE, budget);
            total = result.totalCount();
            for (RegionVisitor visitor : result.items()) {
                if (!visitor.type().isTourist()) {
                    continue; // 현지인 제외
                }
                byCode.computeIfAbsent(visitor.signguCode(), code -> new VisitorAgg())
                        .add(visitor.baseDate(), visitor.count());
            }
            fetched += result.items().size();
            if (result.items().isEmpty() || fetched >= total) {
                return byCode;
            }
        }
        log.warn("관광빅데이터 페이지 상한({}) 도달 — fetched={} total={}. 랭킹이 부분 데이터 기반일 수 있음", MAX_PAGES, fetched, total);
        return byCode;
    }

    /**
     * 한 지역의 방문자 통계를 뽑는다. 코드로 버킷을 못 찾으면 방문자 0(관측일수 0)이다.
     *
     * <p>집계가 비지 않았는데 특정 지역만 못 찾았다면 <b>그 지역의 {@code legal_code} 가 낡았다는 신호</b>다(행정구역 개편
     * 등). 관측일수 0은 베이지안 prior 때문에 <b>전국 평균</b> 점수가 되고, 인구감소지역은 대부분 전국 평균보다 한산하므로 그
     * 지역이 실제보다 <b>높게</b> 올라간다. 예외도 사용자 영향도 없이 순위만 조용히 틀어지므로 warn 을 남긴다.
     */
    private RegionVisitorStat statOf(Region region, Map<String, VisitorAgg> visitorsByCode) {
        VisitorAgg agg = visitorsByCode.get(region.getLegalCode());
        if (agg == null) {
            if (!visitorsByCode.isEmpty()) { // 집계 자체가 비었으면 전 지역이 해당돼 로그가 89줄 쏟아진다
                log.warn("관광빅데이터에 지역 코드가 없습니다 — legal_code 가 낡았는지 확인 필요 regionId={} legalCode={}",
                        region.getId(), region.getLegalCode());
            }
            return new RegionVisitorStat(region.getId(), 0, 0, PopulationDeclineStatus.TARGET);
        }
        return new RegionVisitorStat(region.getId(), agg.total, agg.observedDays(), PopulationDeclineStatus.TARGET);
    }

    /** 시군구별 방문자 누적 — 관광객 합과 관측 일수(distinct 일자). */
    private static final class VisitorAgg {
        private double total;
        private final Set<LocalDate> dates = new HashSet<>();
        /** 저장분에서 복원할 때는 날짜 목록이 없다 — 개수만 남아 있으므로 그것으로 대신한다. */
        private int restoredDays;

        void add(LocalDate date, double count) {
            total += count;
            dates.add(date);
        }

        int observedDays() {
            return dates.isEmpty() ? restoredDays : dates.size();
        }

        /** DB 에 저장된 집계를 랭킹이 쓰는 모양으로 되돌린다. */
        static VisitorAgg of(double total, int observedDays) {
            VisitorAgg agg = new VisitorAgg();
            agg.total = total;
            agg.restoredDays = observedDays;
            return agg;
        }
    }
}
