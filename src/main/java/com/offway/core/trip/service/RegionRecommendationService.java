package com.offway.core.trip.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.trip.domain.PopulationDeclineStatus;
import com.offway.core.trip.domain.RegionRanking;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.domain.RegionVisitorStat;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.service.dto.RecommendRegions;
import com.offway.core.trip.service.dto.RecommendedRegion;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * 여행지 추천(F3) — MVP. 도달 필터 → 방문자 랭킹 → 혜택·한산도 뱃지. TourAPI 콘텐츠·무드 필터·50km 확장은 후속(#61).
 *
 * <p>도메인 간 참조는 각 도메인의 port/service 로만: 도달시간은 transport({@link TravelTimeProvider}), 방문자수는
 * {@link TourDataLabClient}, 혜택은 policy({@link PolicyService}). 랭킹 규칙은 trip 도메인({@link RegionRanking}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionRecommendationService {

    /** 관광빅데이터는 약 15일 지연되므로, 그 이전 1주를 관측 창으로 쓴다. */
    private static final int DATA_LAG_DAYS = 15;
    private static final int OBSERVE_SPAN_DAYS = 7;
    private static final int VISITOR_PAGE_SIZE = 10_000;

    private final RegionRepository regionRepository;
    private final TravelTimeProvider travelTimeProvider;
    private final TourDataLabClient tourDataLabClient;
    private final PolicyService policyService;

    public List<RecommendedRegion> recommend(RecommendRegions command) {
        Coordinate origin = new Coordinate(command.originLat(), command.originLng());

        // 1. 도달 필터 — 도달시간(직선거리 interim) ≤ 한계
        Map<Long, Integer> reachByRegion = new HashMap<>();
        List<Region> reachable = new ArrayList<>();
        for (Region region : regionRepository.findAll()) {
            int reach = travelTimeProvider.reachMinutes(
                    origin, new Coordinate(region.getLat(), region.getLng()), command.transport());
            if (reach <= command.maxReachMinutes()) {
                reachByRegion.put(region.getId(), reach);
                reachable.add(region);
            }
        }
        if (reachable.isEmpty()) {
            return List.of();
        }

        // 2. 방문자 랭킹 — 관광빅데이터를 지역별로 집계(관광객=외지인+외국인)
        Map<String, VisitorAgg> visitorsByName = aggregateTourists();
        List<RegionVisitorStat> stats = reachable.stream().map(region -> statOf(region, visitorsByName)).toList();
        List<RegionScore> ranked = RegionRanking.rank(stats);

        // 3. 조립 — 랭킹순 + 혜택 뱃지
        Map<Long, Region> regionById = new HashMap<>();
        reachable.forEach(region -> regionById.put(region.getId(), region));
        LocalDate today = LocalDate.now();

        List<RecommendedRegion> result = new ArrayList<>();
        for (RegionScore score : ranked) {
            Region region = regionById.get(score.regionId());
            List<RecommendedRegion.Benefit> benefits = policyService.matchForRegion(region.getId(), today).stream()
                    .map(RegionRecommendationService::toBenefit)
                    .toList();
            result.add(new RecommendedRegion(
                    region.getId(), region.getSido(), region.getSigungu(),
                    reachByRegion.get(region.getId()), score.crowdLevel(), benefits));
        }
        log.info("여행지 추천 reachable={} ranked={}", reachable.size(), result.size());
        return result;
    }

    /** 관측 창의 관광빅데이터를 시군구명으로 집계한다(관광객 합·관측 일수). 키 없으면 빈 결과(로컬 실행성). */
    private Map<String, VisitorAgg> aggregateTourists() {
        LocalDate to = LocalDate.now().minusDays(DATA_LAG_DAYS);
        LocalDate from = to.minusDays(OBSERVE_SPAN_DAYS - 1);
        var result = tourDataLabClient.findRegionVisitors(from, to, 1, VISITOR_PAGE_SIZE);
        if (result.totalCount() > result.items().size()) {
            log.warn("관광빅데이터 일부만 조회됨 total={} fetched={} — 랭킹이 부분 데이터 기반", result.totalCount(), result.items().size());
        }
        Map<String, VisitorAgg> byName = new HashMap<>();
        for (RegionVisitor visitor : result.items()) {
            if (!visitor.type().isTourist()) {
                continue; // 현지인 제외
            }
            byName.computeIfAbsent(visitor.signguName(), name -> new VisitorAgg()).add(visitor.baseDate(), visitor.count());
        }
        return byName;
    }

    private RegionVisitorStat statOf(Region region, Map<String, VisitorAgg> visitorsByName) {
        VisitorAgg agg = visitorsByName.get(region.getSigungu());
        if (agg == null) {
            return new RegionVisitorStat(region.getId(), 0, 0, PopulationDeclineStatus.TARGET);
        }
        return new RegionVisitorStat(region.getId(), agg.total, agg.dates.size(), PopulationDeclineStatus.TARGET);
    }

    private static RecommendedRegion.Benefit toBenefit(Policy policy) {
        return new RecommendedRegion.Benefit(policy.getId(), policy.badgeText());
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
