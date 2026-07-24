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
        // 불변식 — 파싱·집계가 보장한다. 여기 닿는 위반은 상류 버그다.
        // NaN·무한대는 음수 검사를 통과하므로 함께 막는다 — 점수·정렬로 전파되면 NaN 이 정상 지역보다
        // 앞서는 등 랭킹이 깨진다.
        if (!Double.isFinite(touristVisitorsTotal) || touristVisitorsTotal < 0) {
            throw new IllegalArgumentException("방문자수는 유한한 음이 아닌 값이어야 합니다: " + touristVisitorsTotal);
        }
        if (observedDays < 0) {
            throw new IllegalArgumentException("관측 일수는 음수일 수 없습니다: " + observedDays);
        }
        // 관측 구간의 누적값이므로, 관측이 0일이면 누적도 0이어야 한다. 아니면 raw 누적값이 랭킹 점수로 샌다.
        if (observedDays == 0 && touristVisitorsTotal != 0) {
            throw new IllegalArgumentException("관측 일수가 0이면 누적 방문자수도 0이어야 합니다: " + touristVisitorsTotal);
        }
    }

    /** 실측 일평균 방문자수. 관측 일수가 0이면 0. */
    public double meanDaily() {
        return observedDays == 0 ? 0 : touristVisitorsTotal / observedDays;
    }
}
