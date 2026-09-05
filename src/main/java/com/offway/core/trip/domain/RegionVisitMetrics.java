package com.offway.core.trip.domain;

/**
 * 한 지역의 방문 지표(#394) — 화면이 쓰는 두 값.
 *
 * <p>둘 다 <b>없을 수 있다</b>. 표본이 모자라거나(신규 적재), 요일 격차가 미미하거나, 작년 치가 없으면
 * 그 값은 null 이고 화면은 그 줄을 지운다. 이 레포가 이미 그렇게 한다 — {@code overview}·{@code
 * useTime}·{@code festivalPeriod} 가 없으면 앱이 그 칸을 접는다.
 *
 * <p><b>가짜 값을 넣지 않는다.</b> 혼잡도와 추세는 "언제 갈지" 를 정하는 값이라, 지어낸 숫자를 보고
 * 갔다가 틀리면 사용자가 우리가 내리는 <b>모든 숫자</b>를 안 믿는다.
 *
 * @param quietestDay 가장 한산한 요일. 없으면 null
 * @param trend 작년 같은 기간 대비 증감. 없으면 null
 */
public record RegionVisitMetrics(QuietestDay quietestDay, PopularityTrend trend) {

    private static final RegionVisitMetrics NONE = new RegionVisitMetrics(null, null);

    /** 아직 아무것도 낼 수 없는 지역 — 적재 전이거나 표본이 모자라다. */
    public static RegionVisitMetrics none() {
        return NONE;
    }

    /** 요즘 뜨고 있는 지역인가 — 목록 정렬과 "최근 인기 상승" 칩이 이 판정을 쓴다. */
    public boolean isRising() {
        return trend != null && trend.rising();
    }
}
