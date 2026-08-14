package com.offway.core.trip.controller.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.HomeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 응답 — API 계약. 남은 연차 + 필터칩 + 이번주 추천 지역(랭킹 top-N, 대표 이미지·categories·한산도·대표 혜택).
 */
public record HomeResponse(User user, List<CategoryResponse.Item> filters, List<RegionCard> recommendedRegions) {

    private static final String GUEST_NAME = "게스트";

    public static HomeResponse from(HomeResult result) {
        return new HomeResponse(
                new User(GUEST_NAME, result.remainingLeaveDays()),
                CategoryResponse.of(result.categoryCounts()).categories(),
                result.regions().stream().map(RegionCard::from).toList());
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
            @Schema(example = "완도군 · 전라남도") String name,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            List<CategoryTagResponse> categories,
            @Schema(description = "대표 혜택 (없으면 null)", nullable = true) Benefit benefit) {

        static RegionCard from(HomeResult.RegionCard card) {
            return new RegionCard(
                    card.regionId(),
                    card.sigungu() + " · " + card.sido(),
                    card.crowdLevel(),
                    card.imageUrl(),
                    card.categories().stream().map(CategoryTagResponse::from).toList(),
                    card.benefit() == null ? null : Benefit.from(card.benefit()));
        }
    }

    /**
     * @param text 뱃지 문구
     * @param policyType 정책 분류
     * @param policyId 정책 ID
     */
    public record Benefit(String text, PolicyType policyType, long policyId) {

        static Benefit from(HomeResult.Benefit benefit) {
            return new Benefit(benefit.text(), benefit.type(), benefit.policyId());
        }
    }
}
