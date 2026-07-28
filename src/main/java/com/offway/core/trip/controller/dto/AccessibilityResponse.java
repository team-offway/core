package com.offway.core.trip.controller.dto;

import com.offway.core.trip.service.dto.AccessibilityFeature;
import com.offway.core.trip.service.dto.PoiAccessibility;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 장소 무장애 정보 응답 — API 계약. 등록된 이용약자 편의를 분류·항목명·상세로 내린다. 등록 정보가 없으면 {@code features} 는 빈 배열.
 *
 * @param contentId 콘텐츠 ID
 * @param features 등록된 무장애 편의(없으면 빈 배열)
 */
public record AccessibilityResponse(String contentId, List<Feature> features) {

    public static AccessibilityResponse from(PoiAccessibility accessibility) {
        return new AccessibilityResponse(
                accessibility.contentId(),
                accessibility.features().stream().map(Feature::from).toList());
    }

    /**
     * 무장애 편의 한 항목.
     *
     * @param category 분류 코드(그룹핑용 안정 키 · MOBILITY·VISUAL·HEARING·INFANT)
     * @param categoryLabel 분류 한글명(이동약자·시각장애·청각장애·영유아·가족)
     * @param name 편의 항목명
     * @param detail 관광지가 등록한 상세 문구
     */
    public record Feature(
            @Schema(example = "MOBILITY") String category,
            @Schema(example = "이동약자") String categoryLabel,
            @Schema(example = "휠체어") String name,
            @Schema(example = "대여가능") String detail) {

        static Feature from(AccessibilityFeature feature) {
            return new Feature(
                    feature.category().name(),
                    feature.category().label(),
                    feature.name(),
                    feature.detail());
        }
    }
}
