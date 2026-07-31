package com.offway.core.trip.service;

import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.HomeResult;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.service.AirQualityService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 홈 화면 조율(F3) — 남은 연차 + 이번주 추천 지역(랭킹 top-N, 대표 이미지·categories·한산도·대표 혜택). 도달 필터 없이 전 지역
 * 랭킹에서 상위를 뽑는다. 무드 필터는 추천(S3)에서만 건다.
 *
 * <p>TourAPI 콘텐츠 호출은 read-timeout 이 길어 트랜잭션으로 묶지 않는다(persistence-convention). 랭킹·리포지토리가 각자 짧은
 * 트랜잭션을 갖고, 콘텐츠 부착은 tx 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 홈에 노출하는 추천 지역 수. */
    private static final int HOME_REGION_LIMIT = 6;

    private final RegionRepository regionRepository;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final PolicyService policyService;
    private final AirQualityService airQualityService;
    private final MyLeaveService myLeaveService;

    /**
     * 홈 화면. 남은 연차는 <b>저장값에서 읽는다</b> — 예전엔 클라이언트가 보낸 값을 그대로 되돌려줬다(#89).
     *
     * @param guestId 소유 키 (없으면 남은 연차는 null — "미설정" 과 0 을 구분한다)
     */
    public HomeResult home(String guestId) {
        Double remainingLeaveDays = myLeaveService.remainingDaysOrNull(guestId);
        List<Region> all = regionRepository.findAll();
        List<RegionScore> ranked = regionRankingService.rankByVisitors(all);

        Map<Long, Region> regionById = new HashMap<>();
        all.forEach(region -> regionById.put(region.getId(), region));
        LocalDate today = LocalDate.now();

        List<RegionScore> top = ranked.stream().limit(HOME_REGION_LIMIT).toList();
        List<Region> topRegions = top.stream().map(score -> regionById.get(score.regionId())).toList();

        // 외부(TourAPI)는 병렬, DB(혜택)는 일괄. 대기질은 시도 단위라 top-N 에서 겹치므로 시도별 1회만 조회한다
        // — 캐시가 있어 대부분 즉답이고, 순차로 둬야 아래 맵을 스레드 안전성 걱정 없이 쓸 수 있다.
        Map<Long, RegionContent> contents = regionContentProvider.contentForAll(topRegions, all, RegionContentProvider.REQUEST_FANOUT_DEADLINE);
        Map<Long, List<Policy>> policiesByRegion =
                policyService.matchForRegions(topRegions.stream().map(Region::getId).toList(), today);
        Map<String, Optional<AirQuality>> airBySido = new HashMap<>();

        List<HomeResult.RegionCard> cards = top.stream()
                .map(score -> toCard(
                        regionById.get(score.regionId()), score, contents, policiesByRegion, airBySido))
                .toList();
        return new HomeResult(remainingLeaveDays, cards);
    }

    private HomeResult.RegionCard toCard(
            Region region,
            RegionScore score,
            Map<Long, RegionContent> contents,
            Map<Long, List<Policy>> policiesByRegion,
            Map<String, Optional<AirQuality>> airBySido) {
        RegionContent content = contents.getOrDefault(region.getId(), RegionContent.EMPTY);
        List<Policy> matched = policiesByRegion.getOrDefault(region.getId(), List.of());
        HomeResult.Benefit benefit = matched.isEmpty() ? null : toBenefit(matched.get(0));
        AirQuality air = airBySido
                .computeIfAbsent(region.getSido(), airQualityService::byRegionSido)
                .orElse(null);
        return HomeResult.RegionCard.of(
                region.getId(), region.getSido(), region.getSigungu(), score.crowdLevel(), content, benefit, air);
    }

    private static HomeResult.Benefit toBenefit(Policy policy) {
        return new HomeResult.Benefit(policy.getId(), policy.getType(), policy.badgeText());
    }
}
