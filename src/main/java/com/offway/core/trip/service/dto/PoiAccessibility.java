package com.offway.core.trip.service.dto;

import java.util.List;

/**
 * 장소의 무장애(배리어프리) 정보 — 등록된 편의 항목 목록. 등록 정보가 없으면 {@code features} 는 빈 목록(장소 미존재와 구분하지 않음:
 * detailWithTour2 는 정상 응답으로 0건을 준다).
 *
 * @param contentId 콘텐츠 ID
 * @param features 등록된 무장애 편의(없으면 빈 목록)
 */
public record PoiAccessibility(String contentId, List<AccessibilityFeature> features) {

    /** 등록된 무장애 편의가 하나도 없는 상태. */
    public static PoiAccessibility empty(String contentId) {
        return new PoiAccessibility(contentId, List.of());
    }
}
