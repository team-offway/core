package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RecommendedRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 여행지 추천 응답 — API 계약. 랭킹 순(무드 지정 시 매칭 지역 우선).
 *
 * @param regions 추천 지역 (랭킹 내림차순)
 */
public record RegionRecommendResponse(List<Item> regions) {

    public static RegionRecommendResponse from(List<RecommendedRegion> regions) {
        return new RegionRecommendResponse(regions.stream().map(Item::from).toList());
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param reachMinutes 출발지→지역 도달시간(분)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param contentCount 볼거리 수 (인접 50km 병합 시 합산)
     * @param categories 볼거리 카테고리 칩
     * @param neighborIncluded 볼거리 부족으로 인접 50km 지역이 포함됐는지
     * @param benefits 적용 혜택 뱃지
     */
    public record Item(
            long regionId,
            @Schema(example = "완도군 · 전라남도") String name,
            @Schema(example = "160") int reachMinutes,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            @Schema(example = "38") int contentCount,
            List<CategoryResponse.Item> categories,
            @Schema(example = "false") boolean neighborIncluded,
            List<Benefit> benefits) {

        static Item from(RecommendedRegion region) {
            return new Item(
                    region.regionId(),
                    region.sigungu() + " · " + region.sido(),
                    region.reachMinutes(),
                    region.crowdLevel(),
                    region.imageUrl(),
                    region.contentCount(),
                    region.categories().stream().map(CategoryResponse.Item::from).toList(),
                    region.neighborIncluded(),
                    region.benefits().stream().map(Benefit::from).toList());
        }
    }

    /**
     * @param policyId 정책 ID
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, String text) {

        static Benefit from(RecommendedRegion.Benefit benefit) {
            return new Benefit(benefit.policyId(), benefit.text());
        }
    }
}
