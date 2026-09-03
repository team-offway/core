package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CategoryCounts;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import java.util.List;
import lombok.Builder;

/**
 * 홈 화면 데이터 — 서비스 내부 result. 남은 연차 + 이번주 추천 지역(랭킹 top-N).
 *
 * <p>홈 카드는 대표 이미지·볼거리 categories 와 대표 혜택 하나를 붙인다(뱃지 강조). 무드 필터는 추천(S3)에서만 건다.
 *
 * @param remainingLeaveDays 남은 연차 (저장값. 설정한 적 없으면 null — 0 과 구분한다). 반차가 0.5 라 실수다
 * @param regions 추천 지역 카드 (랭킹 top-N)
 * @param categoryCounts 필터칩별 지역 수(#266). 홈이 그리는 칩이 개수를 지어내지 않게 함께 내린다
 * @param places <b>이번달 추천 여행지</b> 섹션의 장소 카드(#305). {@code regions} 와 다른 섹션이다 —
 *     그쪽은 "이번 연차엔 여기 어때요?" 로, 지역명과 대표 이미지만 쓴다
 */
public record HomeResult(
        Double remainingLeaveDays,
        List<RegionCard> regions,
        CategoryCounts categoryCounts,
        List<PlaceCard> places) {

    /**
     * 장소 카드 하나 — 시안의 위쪽 섹션(#305).
     *
     * <p><b>카드가 장소여야 칩이 뜻을 갖는다.</b> 지역은 네 칩을 동시에 가지므로 "숙박" 을 눌러도 같은
     * 지역이 그대로 남는다. 골라도 달라지는 게 없던 것이 이 섹션이 새로 필요한 이유다.
     *
     * @param poiContentId 장소 상세({@code GET /pois/{id}})로 가는 키
     * @param kind 이 장소가 걸린 칩. 앱이 이 값으로 필터를 건다
     * @param regionId 어느 지역인지(#318). <b>같은 응답의 {@code regions} 중 하나와 반드시 일치한다</b> —
     *     장소는 그 지역들에서만 뽑기 때문이다
     * @param regionName 어느 지역인지 — 카드가 장소라 지역명을 따로 실어야 한다
     * @param subtitle 장소명 아래 한 줄. <b>없을 수 있다</b> — 재료가 없으면 앱이 그 줄을 접는다
     * @param benefit 그 지역에 걸리는 대표 혜택(없으면 null)
     */
    @Builder
    public record PlaceCard(
            String poiContentId,
            String name,
            String imageUrl,
            Category kind,
            long regionId,
            String regionName,
            String subtitle,
            RegionBenefit benefit) {
    }

    /**
     * @param regionId 지역 ID
     * @param sido 시도
     * @param sigungu 시군구
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL(없으면 null)
     * @param categories 볼거리 카테고리
     * @param benefit 대표 혜택(없으면 null)
     */
    public record RegionCard(
            long regionId,
            String sido,
            String sigungu,
            CrowdLevel crowdLevel,
            String imageUrl,
            List<Category> categories,
            RegionBenefit benefit) {

        /**
         * 랭킹·혜택에 지역 콘텐츠(이미지·categories)를 얹어 카드를 만든다.
         *
         * <p><b>대표 사진은 사다리로 고른다</b>(#196) — 중심 관광지 × 관광사진 갤러리가 먼저고, 못 고르면
         * TourAPI 표본의 이미지로 내려간다. 둘 다 없으면 null 이고 자리표시자는 FE 가 그린다.
         *
         * @param heroPhotoUrl 갤러리에서 고른 대표 사진. 못 골랐으면 null
         */
        public static RegionCard of(
                long regionId,
                String sido,
                String sigungu,
                CrowdLevel crowdLevel,
                RegionContent content,
                String heroPhotoUrl,
                RegionBenefit benefit) {
            return new RegionCard(
                    regionId,
                    sido,
                    sigungu,
                    crowdLevel,
                    heroPhotoUrl != null ? heroPhotoUrl : content.imageUrl(),
                    content.categories(),
                    benefit);
        }
    }
}
