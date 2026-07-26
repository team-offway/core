package com.offway.core.trip.service.dto;

import com.offway.core.trip.domain.CrowdLevel;
import java.util.List;

/**
 * 추천 결과 한 건(랭킹 순). 콘텐츠(볼거리 수·이미지·categories)·무드 필터는 후속(#61)에서 붙는다.
 *
 * @param regionId 지역 식별자
 * @param sido 시도
 * @param sigungu 시군구
 * @param reachMinutes 출발지→지역 도달시간(분)
 * @param crowdLevel 한산도 뱃지(표시용)
 * @param benefits 이 지역에 적용되는 혜택 뱃지
 */
public record RecommendedRegion(
        long regionId, String sido, String sigungu, int reachMinutes, CrowdLevel crowdLevel, List<Benefit> benefits) {

    /**
     * @param policyId 정책 ID
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, String text) {
    }
}
