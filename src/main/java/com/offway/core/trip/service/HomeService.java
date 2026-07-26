package com.offway.core.trip.service;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.HomeResult;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 화면 조율(F3) — 남은 연차 + 이번주 추천 지역(랭킹 top-N, 한산도·대표 혜택). 도달 필터 없이 전 지역 랭킹에서 상위를 뽑는다.
 * 무드 필터·콘텐츠(이미지·요약)는 후속(#61).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    /** 홈에 노출하는 추천 지역 수. */
    private static final int HOME_REGION_LIMIT = 6;

    private final RegionRepository regionRepository;
    private final RegionRankingService regionRankingService;
    private final PolicyService policyService;

    public HomeResult home(Integer remainingLeaveDays) {
        List<Region> all = regionRepository.findAll();
        List<RegionScore> ranked = regionRankingService.rankByVisitors(all);

        Map<Long, Region> regionById = new HashMap<>();
        all.forEach(region -> regionById.put(region.getId(), region));
        LocalDate today = LocalDate.now();

        List<HomeResult.RegionCard> cards = ranked.stream()
                .limit(HOME_REGION_LIMIT)
                .map(score -> toCard(regionById.get(score.regionId()), score, today))
                .toList();
        return new HomeResult(remainingLeaveDays, cards);
    }

    private HomeResult.RegionCard toCard(Region region, RegionScore score, LocalDate date) {
        List<Policy> matched = policyService.matchForRegion(region.getId(), date);
        HomeResult.Benefit benefit = matched.isEmpty() ? null : toBenefit(matched.get(0));
        return new HomeResult.RegionCard(
                region.getId(), region.getSido(), region.getSigungu(), score.crowdLevel(), benefit);
    }

    private static HomeResult.Benefit toBenefit(Policy policy) {
        return new HomeResult.Benefit(policy.getId(), policy.getType(), policy.badgeText());
    }
}
