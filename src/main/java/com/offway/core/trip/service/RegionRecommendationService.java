package com.offway.core.trip.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.RecommendRegions;
import com.offway.core.trip.service.dto.RecommendedRegion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행지 추천(F3) — MVP. 도달 필터 → 방문자 랭킹 → 혜택·한산도 뱃지. TourAPI 콘텐츠·무드 필터·50km 확장은 후속(#61).
 *
 * <p>도메인 간 참조는 각 도메인의 port/service 로만: 도달시간은 transport({@link TravelTimeProvider}), 방문자 랭킹은
 * {@link RegionRankingService}, 혜택은 policy({@link PolicyService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionRecommendationService {

    private final RegionRepository regionRepository;
    private final TravelTimeProvider travelTimeProvider;
    private final RegionRankingService regionRankingService;
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

        // 2. 방문자 랭킹 (공유)
        List<RegionScore> ranked = regionRankingService.rankByVisitors(reachable);

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

    private static RecommendedRegion.Benefit toBenefit(Policy policy) {
        return new RecommendedRegion.Benefit(policy.getId(), policy.badgeText());
    }
}
