package com.offway.core.trip.controller.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.service.dto.HomeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 홈 응답 — API 계약. 남은 연차 + 필터칩 + 이번주 추천 지역.
 *
 * <p>MVP: 추천 지역은 랭킹 top-N(한산도·대표 혜택). 이미지·요약·categories·무드 필터는 후속(#61).
 */
public record HomeResponse(User user, List<CategoryResponse.Item> filters, List<RegionCard> recommendedRegions) {

    private static final String GUEST_NAME = "게스트";

    public static HomeResponse from(HomeResult result) {
        return new HomeResponse(
                new User(GUEST_NAME, result.remainingLeaveDays()),
                CategoryResponse.of().categories(),
                result.regions().stream().map(RegionCard::from).toList());
    }

    /**
     * @param name 사용자명 (게스트)
     * @param remainingLeaveDays 남은 연차 (없으면 null)
     */
    public record User(@Schema(example = "게스트") String name, @Schema(example = "13") Integer remainingLeaveDays) {
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param crowdLevel 한산도 뱃지
     * @param benefit 대표 혜택 (없으면 null)
     */
    public record RegionCard(
            long regionId,
            @Schema(example = "완도군 · 전라남도") String name,
            CrowdLevel crowdLevel,
            @Schema(description = "대표 혜택 (없으면 null)") Benefit benefit) {

        static RegionCard from(HomeResult.RegionCard card) {
            return new RegionCard(
                    card.regionId(),
                    card.sigungu() + " · " + card.sido(),
                    card.crowdLevel(),
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
