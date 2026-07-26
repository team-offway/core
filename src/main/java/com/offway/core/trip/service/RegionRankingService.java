package com.offway.core.trip.service;

import com.offway.core.region.domain.Region;
import com.offway.core.trip.domain.PopulationDeclineStatus;
import com.offway.core.trip.domain.RegionRanking;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.domain.RegionVisitorStat;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역을 방문자 데이터로 랭킹한다 — 추천(#23)과 홈(#22)이 공유한다. 관광빅데이터를 시군구명으로 집계(관광객=외지인+외국인)해
 * {@link RegionRanking}(베이지안 보정)에 넘긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionRankingService {

    /** 관광빅데이터는 약 15일 지연되므로, 그 이전 1주를 관측 창으로 쓴다. */
    private static final int DATA_LAG_DAYS = 15;
    private static final int OBSERVE_SPAN_DAYS = 7;
    private static final int FIRST_PAGE_NUMBER = 1;
    private static final int VISITOR_PAGE_SIZE = 10_000;
    /** 페이지 폭주 안전장치 — total 이 잘못 크거나 페이지가 안 줄어도 무한 루프에 빠지지 않게 상한을 둔다. */
    private static final int MAX_PAGES = 50;

    private final TourDataLabClient tourDataLabClient;

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
     * 관측 창의 관광빅데이터를 <b>전 페이지</b> 모아 시군구명으로 집계한다(관광객 합·관측 일수). 부분 페이지로 랭킹하면 뒷페이지 지역이
     * 0으로 과소집계돼 순위가 틀어지므로, totalCount 까지 페이지를 끝까지 읽는다. 키 없으면 빈 결과(로컬 실행성).
     */
    private Map<String, VisitorAgg> aggregateTourists() {
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
}
