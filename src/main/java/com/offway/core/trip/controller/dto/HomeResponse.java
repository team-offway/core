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
                CategoryResponse.of().categories(),
                result.regions().stream().map(RegionCard::from).toList());
    }

    /**
     * @param name 사용자명 (게스트)
     * @param remainingLeaveDays 남은 연차 (없으면 null)
     */
    public record User(
            @Schema(example = "게스트") String name,
            @Schema(example = "13", nullable = true) Integer remainingLeaveDays) {
    }

    /**
     * @param regionId 지역 ID
     * @param name 지역명 (시군구 · 시도)
     * @param crowdLevel 한산도 뱃지
     * @param imageUrl 대표 이미지 URL (없으면 null)
     * @param categories 볼거리 카테고리 칩
     * @param benefit 대표 혜택 (없으면 null)
     * @param airQuality 지역 시도 실시간 대기질 (없으면 null)
     */
    public record RegionCard(
            long regionId,
            @Schema(example = "완도군 · 전라남도") String name,
            CrowdLevel crowdLevel,
            @Schema(
                            example = "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
                            nullable = true)
                    String imageUrl,
            List<CategoryResponse.Item> categories,
            @Schema(description = "대표 혜택 (없으면 null)", nullable = true) Benefit benefit,
            @Schema(description = "지역 실시간 대기질 (없으면 null)", nullable = true) AirQuality airQuality) {

        static RegionCard from(HomeResult.RegionCard card) {
            return new RegionCard(
                    card.regionId(),
                    card.sigungu() + " · " + card.sido(),
                    card.crowdLevel(),
                    card.imageUrl(),
                    card.categories().stream().map(CategoryResponse.Item::from).toList(),
                    card.benefit() == null ? null : Benefit.from(card.benefit()),
                    card.airQuality() == null ? null : AirQuality.from(card.airQuality()));
        }
    }

    /**
     * @param pm10 미세먼지 평균 (㎍/㎥, 없으면 null)
     * @param pm25 초미세먼지 평균 (㎍/㎥, 없으면 null)
     * @param grade 통합대기환경 등급 문구 (좋음·보통·나쁨·매우나쁨·정보없음)
     */
    public record AirQuality(
            @Schema(example = "45", nullable = true) Integer pm10,
            @Schema(example = "23", nullable = true) Integer pm25,
            @Schema(example = "보통") String grade) {

        static AirQuality from(com.offway.core.weather.domain.AirQuality air) {
            return new AirQuality(air.pm10(), air.pm25(), air.grade().label());
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
