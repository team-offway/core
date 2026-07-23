package com.offway.core.policy.controller.dto;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import com.offway.core.policy.service.dto.PolicyWithRegions;
import com.offway.core.region.domain.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 정책 상세 응답 — API 계약. 정책 정보 + 이 혜택이 되는 여행지 목록.
 */
public record PolicyResponse(
        @Schema(example = "1") Long id,
        PolicyType type,
        @Schema(example = "지역사랑 휴가지원(반값여행)") String name,
        @Schema(description = "지역 카드용 짧은 뱃지 문구", example = "여행경비 50% 환급") String badgeText,
        @Schema(example = "여행경비의 50%를 지역화폐로 환급 · 1인 최대 10만원(청년 70%)") String benefitDetail,
        @Schema(description = "운영 기간 (없으면 null = 상시)") Period period,
        @Schema(description = "지원 대상", example = "전 국민(거주지와 다른 지역 여행 시)") String target,
        String applyUrl,
        @Schema(description = "이 혜택이 되는 여행지") List<RegionSummary> regions) {

    public static PolicyResponse from(PolicyWithRegions detail) {
        Policy policy = detail.policy();
        return new PolicyResponse(
                policy.getId(),
                policy.getType(),
                policy.getName(),
                policy.badgeText(),
                policy.getBenefitDetail(),
                Period.of(policy.getPeriodStart(), policy.getPeriodEnd()),
                policy.getTargetAudience(),
                policy.getApplyUrl(),
                detail.regions().stream().map(RegionSummary::from).toList());
    }

    /**
     * @param start 운영 시작일
     * @param end 운영 종료일
     */
    public record Period(LocalDate start, LocalDate end) {

        /** 시작·종료가 모두 없으면 상시 운영으로 보고 null 을 돌려준다. */
        static Period of(LocalDate start, LocalDate end) {
            if (start == null && end == null) {
                return null;
            }
            return new Period(start, end);
        }
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param imageUrl 대표 이미지 (TourAPI 연동 전이면 null)
     */
    public record RegionSummary(Long regionId, String name, String imageUrl) {

        static RegionSummary from(Region region) {
            return new RegionSummary(region.getId(), region.getSigungu() + " · " + region.getSido(), null);
        }
    }
}
