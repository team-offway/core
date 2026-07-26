package com.offway.core.trip.service.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import java.util.List;

/**
 * 홈 화면 데이터 — 서비스 내부 result. 남은 연차 + 이번주 추천 지역(랭킹 top-N).
 *
 * <p>홈 카드는 대표 이미지·볼거리 categories 와 대표 혜택 하나를 붙인다(뱃지 강조). 무드 필터는 추천(S3)에서만 건다.
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
            Benefit benefit) {

        /** 랭킹·혜택에 지역 콘텐츠(이미지·categories)를 얹어 카드를 만든다. */
        public static RegionCard of(
                long regionId,
                String sido,
                String sigungu,
                CrowdLevel crowdLevel,
                RegionContent content,
                Benefit benefit) {
            return new RegionCard(
                    regionId, sido, sigungu, crowdLevel, content.imageUrl(), content.categories(), benefit);
        }
    }

    /**
     * @param policyId 정책 ID
     * @param type 정책 분류
     * @param text 뱃지 문구
     */
    public record Benefit(long policyId, PolicyType type, String text) {
    }
}
