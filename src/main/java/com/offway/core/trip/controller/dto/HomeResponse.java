package com.offway.core.trip.controller.dto;

import com.offway.core.curation.controller.dto.CuratedLinkResponse;
import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.HomeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 응답 — API 계약.
 *
 * <p><b>섹션이 둘이다.</b> 시안의 위쪽은 장소 카드({@code recommendedPlaces}), 아래쪽은 지역 카드
 * ({@code recommendedRegions})다. 이름만으로 어느 섹션 것인지 드러나지 않아 여기 적어 둔다 —
 * 아래 것을 위 섹션에 쓰다가 제목에 지역명이 두 번 나온 적이 있다(#305).
 *
 * @param curatedLinks 외부 페이지로 나가는 창구(#341). 홈에 켜진 것만, 정렬 순으로. 없으면 <b>빈 목록</b>이다
 */
public record HomeResponse(
        User user,
        List<CategoryResponse.Item> filters,
        List<PlaceCard> recommendedPlaces,
        List<RegionCard> recommendedRegions,
        List<CuratedLinkResponse> curatedLinks) {

    private static final String GUEST_NAME = "게스트";

    public static HomeResponse from(HomeResult result, List<CuratedLink> curatedLinks) {
        return new HomeResponse(
                new User(GUEST_NAME, result.remainingLeaveDays()),
                CategoryResponse.of(result.categoryCounts()).categories(),
                result.places().stream().map(PlaceCard::from).toList(),
                result.regions().stream().map(RegionCard::from).toList(),
                CuratedLinkResponse.from(curatedLinks));
    }

    /**
     * <b>이번달 추천 여행지</b> 섹션의 카드 — 지역이 아니라 장소다(#305).
     *
     * @param poiContentId 장소 상세({@code GET /api/v1/pois/{poiId}})로 가는 키
     * @param name 장소명. 시안의 제목이 이것이다
     * @param imageUrl 대표 이미지 (없는 장소는 애초에 안 실린다)
     * @param kind 이 장소가 걸린 칩 — 앱이 이 값으로 필터를 건다
     * @param regionId 지역 ID(#318). 이름만으로는 <b>동명 시군구</b>를 가릴 수 없어 함께 싣는다 —
     *     동구는 부산·대구·인천에 다 있다. 이 값은 같은 응답의 {@code recommendedRegions} 중 하나와
     *     반드시 일치하므로, 앱은 시도 표기를 그쪽에서 그대로 가져다 쓸 수 있다
     * @param regionName 시군구명. 카드가 장소라 지역을 따로 싣는다
     * @param subtitle 장소명 아래 한 줄. <b>없으면 null</b> — 앱이 그 줄을 접는다. 재료가 없는 장소가
     *     실제로 있다(캠핑장·레포츠)
     * @param benefit 그 지역의 대표 혜택 (없으면 null)
     */
    public record PlaceCard(
            @Schema(example = "126508") String poiContentId,
            @Schema(example = "삼탄아트마인") String name,
            @Schema(example = "http://tong.visitkorea.or.kr/cms/resource/…") String imageUrl,
            @Schema(example = "SIGHT") Category kind,
            @Schema(example = "76") long regionId,
            @Schema(example = "정선군") String regionName,
            @Schema(example = "폐광촌에서 다시 태어난 마을", nullable = true) String subtitle,
            @Schema(nullable = true) BenefitResponse benefit) {

        public static PlaceCard from(HomeResult.PlaceCard card) {
            return new PlaceCard(
                    card.poiContentId(),
                    card.name(),
                    card.imageUrl(),
                    card.kind(),
                    card.regionId(),
                    card.regionName(),
                    card.subtitle(),
                    BenefitResponse.from(card.benefit()));
        }
    }

    /**
     * @param name 사용자명 (게스트)
     * @param remainingLeaveDays 남은 연차 (저장값. 설정한 적 없으면 null). 반차가 0.5 라 실수다
     */
    public record User(
            @Schema(example = "게스트") String name,
            @Schema(example = "13.0", nullable = true) Double remainingLeaveDays) {
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param categories 볼거리 카테고리 태그 (필터칩과 달리 개수가 없다 — {@link CategoryTagResponse})
     * @param benefit 대표 혜택 (없으면 null)
     */
    public record RegionCard(
            long regionId,
            @Schema(example = "완도군 · 전남광주통합특별시") String name,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            List<CategoryTagResponse> categories,
            @Schema(description = "대표 혜택 (없으면 null)", nullable = true) BenefitResponse benefit) {

        static RegionCard from(HomeResult.RegionCard card) {
            return new RegionCard(
                    card.regionId(),
                    card.sigungu() + " · " + card.sido(),
                    card.crowdLevel(),
                    card.imageUrl(),
                    card.categories().stream().map(CategoryTagResponse::from).toList(),
                    BenefitResponse.from(card.benefit()));
        }
    }

}
