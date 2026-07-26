package com.offway.core.trip.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.service.TravelTimeProvider;
import com.offway.core.trip.domain.RegionContent;
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

/**
 * 여행지 추천(F3). 도달 필터 → 방문자 랭킹 → 콘텐츠 부착(볼거리·이미지·categories, 부족 시 50km 확장) → 무드 필터 → 혜택 조립.
 *
 * <p>도메인 간 참조는 각 도메인의 port/service 로만: 도달시간은 transport({@link TravelTimeProvider}), 방문자 랭킹은
 * {@link RegionRankingService}, 콘텐츠는 {@link RegionContentProvider}(TourAPI), 혜택은 policy({@link PolicyService}).
 *
 * <p>TourAPI 콘텐츠 호출은 read-timeout 이 길어 <b>트랜잭션으로 묶지 않는다</b>(DB 커넥션 점유 방지). 각 협력자(리포지토리·랭킹)가
 * 자기 트랜잭션을 갖고, 이 조율 메서드는 tx 밖에서 외부 호출을 끝낸다(persistence-convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionRecommendationService {

    /** 콘텐츠를 붙여 반환하는 후보 상한 — TourAPI 호출량을 반환 후보로 한정한다(랭킹 상위만 노출). */
    private static final int CONTENT_LOOKUP_LIMIT = 20;

    private final RegionRepository regionRepository;
    private final TravelTimeProvider travelTimeProvider;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final PolicyService policyService;

    public List<RecommendedRegion> recommend(RecommendRegions command) {
        Coordinate origin = new Coordinate(command.originLat(), command.originLng());
        List<Region> allRegions = regionRepository.findAll();

        // 1. 도달 필터 — 도달시간(직선거리 interim) ≤ 한계
        Map<Long, Integer> reachByRegion = new HashMap<>();
        List<Region> reachable = new ArrayList<>();
        for (Region region : allRegions) {
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

        // 2. 방문자 랭킹(공유) → 상위 후보만 콘텐츠 부착 대상으로
        List<RegionScore> ranked = regionRankingService.rankByVisitors(reachable);
        List<RegionScore> candidates = ranked.stream().limit(CONTENT_LOOKUP_LIMIT).toList();
        if (ranked.size() > candidates.size()) {
            log.info("추천 후보 상한 적용 ranked={} → {}건 노출", ranked.size(), candidates.size());
        }

        // 3. 후보별 콘텐츠 부착(TourAPI, 부족 시 50km 확장) + 혜택 조립 — tx 밖 외부 호출
        Map<Long, Region> regionById = new HashMap<>();
        reachable.forEach(region -> regionById.put(region.getId(), region));
        LocalDate today = LocalDate.now();

        List<RecommendedRegion> result = new ArrayList<>();
        for (RegionScore score : candidates) {
            Region region = regionById.get(score.regionId());
            RegionContent content = regionContentProvider.contentFor(region, allRegions);
            List<RecommendedRegion.Benefit> benefits = policyService.matchForRegion(region.getId(), today).stream()
                    .map(RegionRecommendationService::toBenefit)
                    .toList();
            result.add(RecommendedRegion.of(
                    region.getId(), region.getSido(), region.getSigungu(),
                    reachByRegion.get(region.getId()), score.crowdLevel(), content, benefits));
        }

        // 4. 무드 필터 — 해당 카테고리 콘텐츠가 있는 지역을 앞세운다(재정렬). 매칭이 하나도 없으면 랭킹 순 유지(빈 결과 방지, F6)
        List<RecommendedRegion> ordered = RecommendedRegion.orderByMood(result, command.mood());
        log.info("여행지 추천 reachable={} 노출={} mood={}", reachable.size(), ordered.size(), command.mood());
        return ordered;
    }

    private static RecommendedRegion.Benefit toBenefit(Policy policy) {
        return new RecommendedRegion.Benefit(policy.getId(), policy.badgeText());
    }
}
