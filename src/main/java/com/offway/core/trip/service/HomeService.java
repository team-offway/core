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
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.repository.PoiIntroRepository;
import com.offway.core.trip.repository.RegionPoiRepository;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 홈 화면 조율(F3) — 남은 연차 + 이번주 추천 지역(랭킹 top-N, 대표 이미지·categories·한산도·대표 혜택). 도달 필터 없이 전 지역
 * 랭킹에서 상위를 뽑는다. 무드 필터는 추천(S3)에서만 건다.
 *
 * <p>TourAPI 콘텐츠 호출은 read-timeout 이 길어 트랜잭션으로 묶지 않는다(persistence-convention). 랭킹·리포지토리가 각자 짧은
 * 트랜잭션을 갖고, 콘텐츠 부착은 tx 밖에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 홈에 노출하는 추천 지역 수. */
    private static final int HOME_REGION_LIMIT = 6;

    /**
     * 장소 카드를 지역·칩마다 몇 건씩 고를지(#305).
     *
     * <p>배치가 받아 둔 수({@code PoiIntroRefreshService.CARDS_PER_CATEGORY})와 <b>같아야 한다</b> —
     * 더 고르면 부제 없는 카드가 섞이고, 덜 고르면 받아 둔 값이 화면에 안 나온다.
     *
     * <p>칩마다 고르는 이유는 필터다. 지역당 상위 N 으로 자르면 등록 수가 많은 칩이 자리를 다 차지해,
     * "숙박" 을 눌렀을 때 빈 목록이 뜬다.
     */
    private static final int PLACES_PER_CATEGORY = 2;

    private final RegionMaster regionMaster;
    private final RegionRankingService regionRankingService;
    private final RegionContentProvider regionContentProvider;
    private final RegionHeroPhotoProvider regionHeroPhotoProvider;
    private final RegionCategoryCountProvider regionCategoryCountProvider;
    private final PolicyService policyService;
    private final MyLeaveService myLeaveService;
    private final RegionPoiRepository regionPoiRepository;
    private final PoiIntroRepository poiIntroRepository;
    private final CatchphraseProvider catchphraseProvider;

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
        return new HomeResult(remainingLeaveDays, cards, regionCategoryCountProvider.counts(),
                placeCards(topRegions, policiesByRegion));
    }

    /**
     * 이번달 추천 여행지 — <b>장소</b> 카드(#305).
     *
     * <p><b>외부를 부르지 않는다.</b> 장소 풀도(#304) 부제 재료도(`poi_intro`) 배치가 미리 채워 둔 값이라
     * DB 만 읽는다. 카드마다 상세를 부르면 홈 한 번에 열 콜이고, 사용자 백 명이면 하루 한도가 마른다.
     *
     * <p><b>부제 재료를 한 번에 가져온다.</b> 카드마다 물으면 그게 곧 N+1 이다.
     *
     * <p>혜택은 지역 카드가 이미 구해 둔 것을 함께 쓴다 — 같은 지역이라 다시 물을 이유가 없다.
     */
    private List<HomeResult.PlaceCard> placeCards(
            List<Region> topRegions, Map<Long, List<Policy>> policiesByRegion) {
        List<Long> regionIds = topRegions.stream().map(Region::getId).toList();
        List<RegionPoi> pois = regionPoiRepository.findForCards(regionIds, PLACES_PER_CATEGORY);
        if (pois.isEmpty()) {
            // 조용히 빈 목록을 내리면 "적재가 안 됐다" 와 "원래 없다" 가 구분되지 않는다.
            log.info("홈 장소 카드 — 지역 {}곳에 내릴 장소가 없습니다", regionIds.size());
            return List.of();
        }
        Map<String, PoiIntro> intros =
                poiIntroRepository.findIntros(pois.stream().map(RegionPoi::getContentId).toList());
        Map<Long, Region> regionById = topRegions.stream()
                .collect(Collectors.toMap(Region::getId, region -> region));

        // 조회는 지역 id 순으로 돌려준다(안정적 순서). 그대로 내리면 <b>랭킹이 무너진다</b> —
        // 지역 카드는 방문자 랭킹 순인데 장소만 id 순이면 두 섹션이 어긋난다. 여기서 랭킹 순으로 세운다.
        Map<Long, Integer> rankByRegion = IntStream.range(0, regionIds.size())
                .boxed()
                .collect(Collectors.toMap(regionIds::get, index -> index));
        return pois.stream()
                .sorted(Comparator
                        .comparingInt((RegionPoi poi) -> rankByRegion.getOrDefault(poi.getRegionId(), Integer.MAX_VALUE))
                        // 칩은 선언 순(관광지 → 숙박 → 체험 → 맛집)이다. 문자열 정렬로 두면
                        // EXPERIENCE·FOOD·SIGHT·STAY 가 되어 화면에 아무 뜻 없는 차례가 된다.
                        .thenComparing(RegionPoi::getCategory)
                        .thenComparing(RegionPoi::getId))
                .map(poi -> toPlaceCard(poi, regionById, intros, policiesByRegion))
                .toList();
    }

    private HomeResult.PlaceCard toPlaceCard(
            RegionPoi poi,
            Map<Long, Region> regionById,
            Map<String, PoiIntro> intros,
            Map<Long, List<Policy>> policiesByRegion) {
        Category kind = poi.getCategory();
        String catchphrase = catchphraseProvider.forContentId(poi.getContentId()).orElse(null);
        // 부제는 칩이 스스로 조합한다 — 카테고리마다 다른 필드를 쓰므로 여기서 분기하면 그 지식이 둘이 된다.
        String subtitle = kind.subtitle(intros.get(poi.getContentId()), catchphrase).orElse(null);
        Region region = regionById.get(poi.getRegionId());
        List<Policy> matched = policiesByRegion.getOrDefault(poi.getRegionId(), List.of());
        return HomeResult.PlaceCard.builder()
                .poiContentId(poi.getContentId())
                .name(poi.getTitle())
                .imageUrl(poi.getImageUrl())
                .kind(kind)
                // 지역명과 달리 id 는 장소가 직접 들고 있어 마스터 조회에 기대지 않는다. 장소를 애초에
                // 상위 지역들에서만 뽑으므로, 이 값은 같은 응답의 지역 카드 중 하나와 늘 일치한다.
                .regionId(poi.getRegionId())
                .regionName(region == null ? null : region.getSigungu())
                .subtitle(subtitle)
                .benefit(matched.isEmpty() ? null : toBenefit(matched.get(0)))
                .build();
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
