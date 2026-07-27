package com.offway.core.weather.infrastructure.airkorea.dto;

import com.offway.core.weather.domain.AirGrade;

/**
 * 시도 단위 대기질 요약 — 시도 내 측정소들의 미세먼지·초미세먼지 평균과 가장 나쁜 통합등급.
 *
 * @param pm10 미세먼지 평균(㎍/㎥, 없으면 null)
 * @param pm25 초미세먼지 평균(㎍/㎥, 없으면 null)
 * @param grade 통합대기환경 등급(측정소 중 최악)
 */
public record AirQuality(Integer pm10, Integer pm25, AirGrade grade) {
}
