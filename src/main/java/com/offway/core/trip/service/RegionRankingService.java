package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.trip.domain.PopulationDeclineStatus;
import com.offway.core.trip.domain.RegionRanking;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.domain.RegionVisitorStat;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역을 방문자 데이터로 랭킹한다 — 추천(#23)과 홈(#22)이 공유한다. 관광빅데이터를 시군구명으로 집계(관광객=외지인+외국인)해
 * {@link RegionRanking}(베이지안 보정)에 넘긴다.
 *
 * <p>생활인구는 <b>랭킹 가중치</b>일 뿐이라 조회 실패로 홈·추천을 막지 않는다(키 없음과 동일 취급 — 방문자 0 폴백). 또 월간 데이터(15일
 * 지연)라 매 요청 재조회할 이유가 없어 인메모리로 캐시하고, 실패 시엔 마지막 성공값을 재사용(stale-while-error)해 느린 외부에 요청이
 * 몰리는 것을 막는다. 외부 호출만 하고 DB 는 건드리지 않으므로 트랜잭션으로 감싸지 않는다(6초 외부 대기 동안 커넥션을 잡지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionRankingService {

    /** 관광빅데이터는 약 15일 지연되므로, 그 이전 1주를 관측 창으로 쓴다. */
    private static final int DATA_LAG_DAYS = 15;
    private static final int OBSERVE_SPAN_DAYS = 7;
    private static final int FIRST_PAGE_NUMBER = 1;
    private static final int VISITOR_PAGE_SIZE = 10_000;
    /** 페이지 폭주 안전장치 — total 이 잘못 크거나 페이지가 안 줄어도 무한 루프에 빠지지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 50;
    /** 성공 캐시 TTL — 월간·15일 지연 데이터라 길게 잡아도 신선도 손해가 없다. */
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    /** 실패 캐시 TTL — 조회가 계속 실패해도 매 요청이 6초씩 재시도하지 않게 짧게 폴백값을 눌러둔다. */
    private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);

    private final TourDataLabClient tourDataLabClient;
    private final AtomicReference<CachedVisitors> cache = new AtomicReference<>();

    /** 방문자 집계 캐시를 비운다 — 운영상 강제 갱신, 그리고 공유 컨텍스트 통합 테스트의 격리(캐시가 이전 시나리오를 물고 가지 않게)용. */
    public void evictCache() {
        cache.set(null);
    }

    /** 주어진 지역들을 방문자 랭킹(점수 내림차순)으로 돌려준다. 키 없으면 방문자 0으로 랭킹(로컬 실행성). */
    public List<RegionScore> rankByVisitors(List<Region> regions) {
        if (regions.isEmpty()) {
            return List.of();
        }
        Map<String, VisitorAgg> visitorsByName = aggregateTourists();
        List<RegionVisitorStat> stats = regions.stream().map(region -> statOf(region, visitorsByName)).toList();
        return RegionRanking.rank(stats);
    }

    /**
     * 시군구별 관광객 집계 — 캐시 우선. 신선하면 캐시값을, 아니면 재조회한다. 조회가 실패하면 마지막 성공값(있으면)을, 없으면 빈 가중치를
     * 폴백으로 쓰고 짧게 캐시해 실패 동안 요청이 몰리지 않게 한다. 어느 경우든 예외를 위로 던지지 않아 홈·추천이 502 로 죽지 않는다.
     */
    private Map<String, VisitorAgg> aggregateTourists() {
        CachedVisitors cached = cache.get();
        if (cached != null && cached.isFresh()) {
            return cached.data();
        }
        try {
            Map<String, VisitorAgg> fresh = fetchTourists();
            cache.set(CachedVisitors.of(fresh, CACHE_TTL));
            return fresh;
        } catch (TourApiException e) {
            Map<String, VisitorAgg> fallback = cached != null ? cached.data() : Map.of();
            cache.set(CachedVisitors.of(fallback, FAILURE_CACHE_TTL));
            log.warn("관광빅데이터 조회 실패 — {} 로 폴백 랭킹(홈·추천은 유지)",
                    cached != null ? "마지막 성공값" : "빈 가중치", e);
            return fallback;
        }
    }

    /**
     * 관측 창의 관광빅데이터를 <b>전 페이지</b> 모아 시군구명으로 집계한다(관광객 합·관측 일수). 부분 페이지로 랭킹하면 뒷페이지 지역이
     * 0으로 과소집계돼 순위가 틀어지므로, totalCount 까지 페이지를 끝까지 읽는다. 키 없으면 빈 결과(로컬 실행성).
     */
    private Map<String, VisitorAgg> fetchTourists() {
        LocalDate to = LocalDate.now().minusDays(DATA_LAG_DAYS);
        LocalDate from = to.minusDays(OBSERVE_SPAN_DAYS - 1);

        Map<String, VisitorAgg> byName = new HashMap<>();
        int fetched = 0;
        int total = 0;
        for (int page = FIRST_PAGE_NUMBER; page < FIRST_PAGE_NUMBER + MAX_PAGES; page++) {
            var result = tourDataLabClient.findRegionVisitors(from, to, page, VISITOR_PAGE_SIZE);
            total = result.totalCount();
            for (RegionVisitor visitor : result.items()) {
                if (!visitor.type().isTourist()) {
                    continue; // 현지인 제외
                }
                byName.computeIfAbsent(visitor.signguName(), name -> new VisitorAgg())
                        .add(visitor.baseDate(), visitor.count());
            }
            fetched += result.items().size();
            if (result.items().isEmpty() || fetched >= total) {
                return byName;
            }
        }
        log.warn("관광빅데이터 페이지 상한({}) 도달 — fetched={} total={}. 랭킹이 부분 데이터 기반일 수 있음", MAX_PAGES, fetched, total);
        return byName;
    }

    private RegionVisitorStat statOf(Region region, Map<String, VisitorAgg> visitorsByName) {
        VisitorAgg agg = visitorsByName.get(region.getSigungu());
        if (agg == null) {
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

    /** 방문자 집계 캐시 한 칸 — 만료 시각까지 재사용. 캐시된 map 은 이후 읽기 전용이라 공유해도 안전. */
    private record CachedVisitors(Map<String, VisitorAgg> data, Instant expiresAt) {
        static CachedVisitors of(Map<String, VisitorAgg> data, Duration ttl) {
            return new CachedVisitors(data, Instant.now().plus(ttl));
        }

        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
