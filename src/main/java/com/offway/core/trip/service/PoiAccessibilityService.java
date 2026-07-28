package com.offway.core.trip.service;

import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.service.dto.PoiAccessibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 무장애(배리어프리) 정보 조회 — TourAPI 무장애정보(detailWithTour2)를 이용약자 편의 목록으로 준다. 장소 상세와 별도
 * 서브리소스로 두어, 무장애 정보가 필요할 때만 외부 호출한다(상세 조회마다 추가 호출을 강제하지 않음 · 실패 격리).
 *
 * <p>외부 호출뿐이라 트랜잭션이 없다. 등록 정보가 없으면 빈 목록(TourAPI 가 정상 응답으로 0건을 준다 — 조회 실패 502 와 구분).
 */
@Service
@RequiredArgsConstructor
public class PoiAccessibilityService {

    private final TourApiClient tourApiClient;

    public PoiAccessibility accessibility(String contentId) {
        return tourApiClient.findAccessibility(contentId)
                .map(TourAccessibility::toPoiAccessibility)
                .orElseGet(() -> PoiAccessibility.empty(contentId));
    }
}
