package com.offway.core.trip.service;

import com.offway.core.leave.service.MyLeaveService;
import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.service.PolicyService;
import com.offway.core.region.domain.Region;
import com.offway.core.region.service.RegionMaster;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.trip.domain.RegionScore;
import com.offway.core.trip.service.dto.HomeResult;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final RegionMaster regionMaster;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final RegionHeroPhotoProvider regionHeroPhotoProvider;
    private final RegionCategoryCountProvider regionCategoryCountProvider;
    private final PolicyService policyService;
    private final MyLeaveService myLeaveService;

    /**
     * 홈 화면. 남은 연차는 <b>저장값에서 읽는다</b> — 예전엔 클라이언트가 보낸 값을 그대로 되돌려줬다(#89).
     *
     * @param userId 인증으로 확인된 사용자. <b>없을 수 있다</b> — 홈은 로그인 앞에 있는 화면이라 비로그인 요청이
     *     정상이고, 그때 남은 연차만 null 이다("미설정" 과 0 을 구분한다)
     */
    public HomeResult home(UUID userId) {
        Double remainingLeaveDays = myLeaveService.remainingDaysOrNull(userId);
        List<Region> all = regionMaster.all();
        List<RegionScore> ranked = regionRankingService.rankByVisitors(all);

        Map<Long, Region> regionById = new HashMap<>();
        all.forEach(region -> regionById.put(region.getId(), region));
        LocalDate today = LocalDate.now();

        List<RegionScore> top = ranked.stream().limit(HOME_REGION_LIMIT).toList();
        List<Region> topRegions = top.stream().map(score -> regionById.get(score.regionId())).toList();

        // 정상 상태에서는 외부를 부르지 않는다 — 콘텐츠·대표사진·혜택이 전부 DB 다. 예전에는 카드마다
        // 예전에는 시도별 대기질을 여기서 채웠다. 느린 시도를 만나면 그 지연을 사용자가 그대로 물어 홈이
        // 24초 걸렸고, 결국 그 값 자체를 걷어냈다 — 코스 판단에 쓰이지 않는데 한도(하루 500)만 먹었다.
        //
        // "하나도 안 부른다" 는 아니다 — 방문자 집계가 <b>통째로 비어 있으면</b> 랭킹이 최초 적재를 한 번
        // 시도한다(RegionRankingService.stored). 빈 환경에서만 걸리는 길이고 single-flight 로 묶여 있으며
        // 실패해도 빈 가중치로 진행한다. 배포마다 되풀이되던 팬아웃과는 성격이 다르다.
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
        // 필터칩 개수는 미리 세어 둔 값을 읽기만 한다(#266) — 요청마다 89곳을 다시 세지 않는다.
        return new HomeResult(remainingLeaveDays, cards, regionCategoryCountProvider.counts());
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
