package com.offway.core.trip.domain;

/**
 * 랭킹 입력 — 한 지역의 관광객(외지인·외국인) 방문 통계. 서비스가 관광빅데이터 응답을 지역별로 집계해 만든다.
 *
 * @param regionId 지역 식별자
 * @param touristVisitorsTotal 관측 구간의 관광객 방문자수 합
 * @param observedDays 데이터가 있는 관측 일수 — 베이지안 표본 크기(콜드스타트 보정용)
 * @param populationDecline 인구감소지역 가점 대상 여부
 */
public record RegionVisitorStat(
        long regionId, double touristVisitorsTotal, int observedDays, boolean populationDecline) {

    public RegionVisitorStat {
        // 불변식 — 파싱·집계가 보장한다. 여기 닿는 음수는 상류 버그다.
        if (touristVisitorsTotal < 0) {
            throw new IllegalArgumentException("방문자수는 음수일 수 없습니다: " + touristVisitorsTotal);
        }
        if (observedDays < 0) {
            throw new IllegalArgumentException("관측 일수는 음수일 수 없습니다: " + observedDays);
        }
    }

    /** 실측 일평균 방문자수. 관측 일수가 0이면 0. */
    public double meanDaily() {
        return observedDays == 0 ? 0 : touristVisitorsTotal / observedDays;
    }
}
