package com.offway.core.trip.service;

import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.cache.ExternalDataCache.Loaded;
import com.offway.core.region.domain.Region;
import com.offway.core.trip.domain.PopulationDeclineStatus;
import com.offway.core.trip.domain.RegionRanking;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.domain.RegionVisitorStat;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import java.time.Duration;
import java.time.LocalDate;
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
    private static final int FIRST_PAGE_NUMBER = 1;
    private static final int VISITOR_PAGE_SIZE = 10_000;
    /** 페이지 폭주 안전장치 — total 이 잘못 크거나 페이지가 안 줄어도 무한 루프에 빠지지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 50;
    /** 성공 캐시 TTL — 월간·15일 지연 데이터라 길게 잡아도 신선도 손해가 없다. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 실패 캐시 TTL — 조회가 계속 실패해도 매 요청이 6초씩 재시도하지 않게 짧게 폴백값을 눌러둔다. */
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);
    /** 방문자 집계는 전 지역 공통 단일 값이라 상수 키 하나로 캐시한다. */
    private static final String VISITORS_KEY = "tourists";

    private final TourDataLabClient tourDataLabClient;
    private final ExternalDataCache<String, Map<String, VisitorAgg>> cache = new ExternalDataCache<>();

    /** 방문자 집계 캐시를 비운다 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트의 격리(캐시가 이전 시나리오를 물고 가지 않게)용. */
    public void evictCache() {
        cache.evictAll();
    }

    /** 주어진 지역들을 방문자 랭킹(점수 내림차순)으로 돌려준다. 키 없으면 방문자 0으로 랭킹(로컬 실행성). */
    public List<RegionScore> rankByVisitors(List<Region> regions) {
        if (regions.isEmpty()) {
            return List.of();
        }
        Map<String, VisitorAgg> visitorsByCode = aggregateTourists();
        List<RegionVisitorStat> stats = regions.stream().map(region -> statOf(region, visitorsByCode)).toList();
        return RegionRanking.rank(stats);
    }

    /**
     * 법정 시군구코드별 관광객 집계 — 캐시(single-flight) 우선. 조회가 실패하면 마지막 성공값(있으면)을, 없으면 빈 가중치를 폴백으로
     * 쓰고 짧게 캐시해 실패 동안 요청이 몰리지 않게 한다. 어느 경우든 예외를 위로 던지지 않아 홈·추천이 502 로 죽지 않는다.
     *
     * <p><b>빈 집계는 성공으로 캐시하지 않는다.</b> 가중치가 없다는 점에서 실패와 결과가 같은데, 성공 TTL(길다)로 눌러두면
     * 무의미한 랭킹이 그만큼 굳는다. 실패와 같은 짧은 TTL 로 두어 재시도를 유도한다.
     */
    private Map<String, VisitorAgg> aggregateTourists() {
        return cache.get(VISITORS_KEY, (key, stale) -> {
            try {
                Map<String, VisitorAgg> fresh = fetchTourists();
                if (fresh.isEmpty()) {
                    return new Loaded<>(fallback(stale, "집계 결과 없음"), FAILURE_CACHE_TTL);
                }
                return new Loaded<>(fresh, CACHE_TTL);
            } catch (TourApiException e) {
                log.warn("관광빅데이터 호출이 예외로 끝났습니다", e); // 원인은 스택으로, 대응은 아래 fallback 로그로
                return new Loaded<>(fallback(stale, "조회 실패"), FAILURE_CACHE_TTL);
            }
        }, Map.of());
    }

    /** 마지막 성공값이 있으면 그것을, 없으면 빈 가중치를 쓴다(홈·추천은 어느 쪽이든 유지된다). */
    private Map<String, VisitorAgg> fallback(Map<String, VisitorAgg> stale, String reason) {
        log.warn("관광빅데이터 {} — {} 로 폴백 랭킹(홈·추천은 유지)", reason, stale != null ? "마지막 성공값" : "빈 가중치");
        return stale != null ? stale : Map.of();
    }

    /**
     * 가장 최근 <b>발행된</b> 달의 관측 창을 집계한다. 지난달부터 시작해 빈 결과면 이전 달로 물러선다 — 발행이 한두 달 밀려도
     * 랭킹이 0 가중치로 죽지 않게. 끝까지 비면 warn 을 남긴다(빈 결과는 예외가 아니라 정상 응답으로 와서, 로그가 없으면
     * 랭킹이 무의미해진 것을 아무도 모른다).
     */
    private Map<String, VisitorAgg> fetchTourists() {
        YearMonth month = YearMonth.from(LocalDate.now()).minusMonths(1);
        for (int back = 0; back < MAX_MONTHS_BACK; back++, month = month.minusMonths(1)) {
            Map<String, VisitorAgg> byCode = fetchMonthTail(month);
            if (!byCode.isEmpty()) {
                return byCode;
            }
            log.info("관광빅데이터 {} 미발행(빈 결과) — 이전 달로 물러섭니다", month);
        }
        log.warn("관광빅데이터 최근 {}개월이 모두 비었습니다 — 방문자 가중치 없이 랭킹합니다(순위가 사실상 무의미)", MAX_MONTHS_BACK);
        return Map.of();
    }

    /**
     * 한 달의 관측 창(마지막 {@value #OBSERVE_SPAN_DAYS}일)을 <b>전 페이지</b> 모아 법정 시군구코드로 집계한다(관광객
     * 합·관측 일수). 부분 페이지로 랭킹하면 뒷페이지 지역이 0으로 과소집계돼 순위가 틀어지므로, totalCount 까지 페이지를
     * 끝까지 읽는다. 키 없으면 빈 결과(로컬 실행성).
     */
    private Map<String, VisitorAgg> fetchMonthTail(YearMonth month) {
        LocalDate to = month.atEndOfMonth();
        LocalDate from = to.minusDays(OBSERVE_SPAN_DAYS - 1);

        Map<String, VisitorAgg> byCode = new HashMap<>();
        int fetched = 0;
        int total = 0;
        for (int page = FIRST_PAGE_NUMBER; page < FIRST_PAGE_NUMBER + MAX_PAGES; page++) {
            var result = tourDataLabClient.findRegionVisitors(from, to, page, VISITOR_PAGE_SIZE);
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
        return new RegionVisitorStat(region.getId(), agg.total, agg.dates.size(), PopulationDeclineStatus.TARGET);
    }

    /** 시군구별 방문자 누적 — 관광객 합과 관측 일수(distinct 일자). */
    private static final class VisitorAgg {
        private double total;
        private final Set<LocalDate> dates = new HashSet<>();

        void add(LocalDate date, double count) {
            total += count;
            dates.add(date);
        }
    }
}
