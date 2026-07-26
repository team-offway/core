package com.offway.core.trip.service.dto;

import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.domain.Category;

/**
 * 여행지 추천 커맨드 — 서비스 내부용.
 *
 * @param originLat 출발지 위도
 * @param originLng 출발지 경도
 * @param transport 이동수단
 * @param maxReachMinutes 편도 도달 한계(분) — 가용시간(LNT) 산출 결과에서 온다
 * @param mood 무드칩(선택) — 지정 시 해당 카테고리 콘텐츠가 있는 지역을 앞세운다. 미지정·{@code ALL} 은 필터 없음
 */
public record RecommendRegions(
        double originLat, double originLng, TransportMode transport, int maxReachMinutes, Category mood) {
}
