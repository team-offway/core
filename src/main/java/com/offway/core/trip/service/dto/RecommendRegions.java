package com.offway.core.trip.service.dto;

import com.offway.core.transport.domain.TransportMode;

/**
 * 여행지 추천 커맨드 — 서비스 내부용.
 *
 * @param originLat 출발지 위도
 * @param originLng 출발지 경도
 * @param transport 이동수단
 * @param maxReachMinutes 편도 도달 한계(분) — 가용시간(LNT) 산출 결과에서 온다
 */
public record RecommendRegions(double originLat, double originLng, TransportMode transport, int maxReachMinutes) {
}
