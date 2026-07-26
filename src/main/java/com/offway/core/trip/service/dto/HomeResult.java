package com.offway.core.trip.service.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.CrowdLevel;
import java.util.List;

/**
 * 홈 화면 데이터 — 서비스 내부 result. 남은 연차 + 이번주 추천 지역(랭킹 top-N).
 *
 * <p>이미지·요약·categories·무드 필터는 후속(#61). 홈 카드는 대표 혜택 하나만 붙인다(뱃지 강조).
 *
 * @param remainingLeaveDays 남은 연차 (게스트 — 클라이언트가 넘긴 값, 없으면 null)
 * @param regions 추천 지역 카드 (랭킹 top-N)
 */
public record HomeResult(Integer remainingLeaveDays, List<RegionCard> regions) {

    /**
     * @param regionId 지역 ID
     * @param sido 시도
     * @param sigungu 시군구
     * @param crowdLevel 한산도 뱃지
     * @param benefit 대표 혜택(없으면 null)
     */
    public record RegionCard(
            long regionId, String sido, String sigungu, CrowdLevel crowdLevel, Benefit benefit) {
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
