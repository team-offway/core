package com.offway.core.trip.controller.dto;

import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.RecommendedRegion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 여행지 추천 응답 — API 계약. 랭킹 순.
 *
 * <p>MVP: 지역 기본정보 + 도달시간 + 한산도 뱃지 + 혜택. 이미지·볼거리 수(contentCount)·categories·무드 필터는 후속(#61).
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
     * @param benefits 적용 혜택 뱃지
     */
    public record Item(
            long regionId,
            @Schema(example = "완도군 · 전라남도") String name,
            @Schema(example = "160") int reachMinutes,
            CrowdLevel crowdLevel,
            List<Benefit> benefits) {

        static Item from(RecommendedRegion region) {
            return new Item(
                    region.regionId(),
                    region.sigungu() + " · " + region.sido(),
                    region.reachMinutes(),
                    region.crowdLevel(),
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
