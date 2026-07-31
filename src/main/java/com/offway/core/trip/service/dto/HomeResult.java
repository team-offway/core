package com.offway.core.trip.service.dto;

import com.offway.core.policy.domain.PolicyType;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.RegionContent;
import com.offway.core.weather.domain.AirQuality;
import java.util.List;

/**
 * 홈 화면 데이터 — 서비스 내부 result. 남은 연차 + 이번주 추천 지역(랭킹 top-N).
 *
 * <p>홈 카드는 대표 이미지·볼거리 categories 와 대표 혜택 하나를 붙인다(뱃지 강조). 무드 필터는 추천(S3)에서만 건다.
 *
 * @param remainingLeaveDays 남은 연차 (저장값. 설정한 적 없으면 null — 0 과 구분한다). 반차가 0.5 라 실수다
 * @param regions 추천 지역 카드 (랭킹 top-N)
 */
public record HomeResult(Double remainingLeaveDays, List<RegionCard> regions) {

    /**
     * @param regionId 지역 ID
     * @param sido 시도
     * @param sigungu 시군구
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL(없으면 null)
     * @param categories 볼거리 카테고리
     * @param benefit 대표 혜택(없으면 null)
     * @param airQuality 지역 시도 실시간 대기질(없으면 null — 부가 정보라 실패해도 카드는 나온다)
     */
    public record RegionCard(
            long regionId,
            String sido,
            String sigungu,
            CrowdLevel crowdLevel,
            String imageUrl,
            List<Category> categories,
            Benefit benefit,
            AirQuality airQuality) {

        /** 랭킹·혜택에 지역 콘텐츠(이미지·categories)와 실시간 대기질을 얹어 카드를 만든다. */
        public static RegionCard of(
                long regionId,
                String sido,
                String sigungu,
                CrowdLevel crowdLevel,
                RegionContent content,
                Benefit benefit,
                AirQuality airQuality) {
            return new RegionCard(
                    regionId,
                    sido,
                    sigungu,
                    crowdLevel,
                    content.imageUrl(),
                    content.categories(),
                    benefit,
                    airQuality);
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
