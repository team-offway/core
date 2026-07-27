package com.offway.core.transport.infrastructure.tmap.dto;

/**
 * TMAP 자동차 경로 결과 — 실측 소요시간·거리.
 *
 * @param durationMinutes 소요시간(분)
 * @param distanceKm 이동거리(㎞)
 */
public record TmapRoute(int durationMinutes, double distanceKm) {
}
