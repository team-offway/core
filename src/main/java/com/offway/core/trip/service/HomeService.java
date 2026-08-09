package com.offway.core.trip.service;

import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.HomeResult;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RegionHeroPhotoProvider regionHeroPhotoProvider;
    private final PolicyService policyService;
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

        // 이 메서드는 이제 외부를 하나도 부르지 않는다 — 전부 DB 다. 예전에는 카드마다 시도별 대기질을
        // 여기서 채웠는데, 에어코리아가 느린 시도를 만나면 그 지연을 사용자가 그대로 물어 홈이 24초 걸렸다.
        // 실시간 대기질이 필요한 자리는 "지금 그 지역에 가 있는" 화면뿐이라 코스로 옮겼다.
        List<Long> topRegionIdsForContent = topRegions.stream().map(Region::getId).toList();
        // 저장된 값만 읽는다(#193) — 요청 경로에서 89곳 팬아웃을 돌리지 않는다.
        Map<Long, RegionContent> contents = regionContentProvider.storedForAll(topRegionIdsForContent);
        List<Long> topRegionIds = topRegions.stream().map(Region::getId).toList();
        Map<Long, List<Policy>> policiesByRegion = policyService.matchForRegions(topRegionIds, today);
        // 대표 사진은 DB 만 읽는다(#196) — 외부 호출이 늘지 않고, 지역마다 묻지 않게 한 번에 가져온다.
        // 홈은 여행월을 모르므로 계절 정렬은 하지 않는다.
        Map<Long, String> heroPhotos = regionHeroPhotoProvider.heroPhotoUrls(topRegionIds, null);

        List<HomeResult.RegionCard> cards = top.stream()
                .map(score -> toCard(
                        regionById.get(score.regionId()), score, contents, heroPhotos, policiesByRegion))
                .toList();
        return new HomeResult(remainingLeaveDays, cards);
    }

    private HomeResult.RegionCard toCard(
            Region region,
            RegionScore score,
            Map<Long, RegionContent> contents,
            Map<Long, String> heroPhotos,
            Map<Long, List<Policy>> policiesByRegion) {
        RegionContent content = contents.getOrDefault(region.getId(), RegionContent.EMPTY);
        List<Policy> matched = policiesByRegion.getOrDefault(region.getId(), List.of());
        HomeResult.Benefit benefit = matched.isEmpty() ? null : toBenefit(matched.get(0));
        return HomeResult.RegionCard.of(
                region.getId(),
                region.getSido(),
                region.getSigungu(),
                score.crowdLevel(),
                content,
                heroPhotos.get(region.getId()),
                benefit);
    }

    private static HomeResult.Benefit toBenefit(Policy policy) {
        return new HomeResult.Benefit(policy.getId(), policy.getType(), policy.badgeText());
    }
}
