package com.offway.core.trip.domain;

import java.time.LocalDate;

/**
 * 한 지역의 하루치 관광객 합(#394) — 유형별로 나뉜 원본을 하루 한 줄로 접은 것.
 *
 * <p><b>접는 일을 DB 에 맡긴다.</b> 원본을 그대로 올리면 89곳 × 15개월 × 30일 × 3유형으로 12만 행인데,
 * 지표 계산이 실제로 보는 것은 "그 지역 그날 관광객 몇 명" 하나뿐이라 접으면 4만 행이 된다. 거주자도
 * 거기서 함께 걸러진다.
 *
 * @param signguCode 법정 시군구코드
 * @param date 기준일자
 * @param tourists 그날 관광객 수 — 거주자를 뺀 외지인·외국인 합
 */
public record RegionDailyTourists(String signguCode, LocalDate date, double tourists) {
}
