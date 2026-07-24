package com.offway.core.trip.domain;

/**
 * 랭킹 결과 한 건 — 지역의 최종 점수와 혼잡도 뱃지. 점수 내림차순이 추천 우선순위다.
 *
 * @param regionId 지역 식별자
 * @param score 랭킹 점수 (방문자 베이지안 보정 + 인구감소 가점)
 * @param crowdLevel 한산도 뱃지 (표시용)
 */
public record RegionScore(long regionId, double score, CrowdLevel crowdLevel) {
}
