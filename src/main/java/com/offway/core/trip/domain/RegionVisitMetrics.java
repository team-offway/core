package com.offway.core.trip.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 한 지역의 방문 지표(#394) — 화면이 쓰는 두 값과, 혼잡 칩의 폴백 재료.
 *
 * <p>셋 다 <b>없을 수 있다</b>. 표본이 모자라거나(신규 적재), 요일 격차가 미미하거나, 작년 치가 없으면
 * 그 값은 null 이고 화면은 그 줄을 지운다. 이 레포가 이미 그렇게 한다 — {@code overview}·{@code
 * useTime}·{@code festivalPeriod} 가 없으면 앱이 그 칸을 접는다.
 *
 * <p><b>가짜 값을 넣지 않는다.</b> 혼잡도와 추세는 "언제 갈지" 를 정하는 값이라, 지어낸 숫자를 보고
 * 갔다가 틀리면 사용자가 우리가 내리는 <b>모든 숫자</b>를 안 믿는다.
 *
 * @param quietestDay 가장 한산한 요일. 없으면 null
 * @param trend 작년 같은 기간 대비 증감. 없으면 null
 * @param weekdayPattern 요일별 방문 패턴 — 집중률 예보가 없는 지역의 혼잡 칩 폴백(#565). 없으면 null.
 *     {@code quietestDay} 도 여기서 나오지만 그건 "격차가 의미 있을 때" 만 값이 되므로, 요일계수를
 *     쓰려면 패턴 자체를 들고 있어야 한다
 */
public record RegionVisitMetrics(
        QuietestDay quietestDay, PopularityTrend trend, WeeklyVisitPattern weekdayPattern) {

    private static final RegionVisitMetrics NONE = new RegionVisitMetrics(null, null, null);

    /** 아직 아무것도 낼 수 없는 지역 — 적재 전이거나 표본이 모자라다. */
    public static RegionVisitMetrics none() {
        return NONE;
    }

    /** 요즘 뜨고 있는 지역인가 — 목록 정렬과 "최근 인기 상승" 칩이 이 판정을 쓴다. */
    public boolean isRising() {
        return trend != null && trend.rising();
    }

    /**
     * 그 날짜에 이 <b>지역</b>이 붐비는가 — 집중률 예보가 없는 지역의 폴백(#565).
     *
     * <p><b>장소가 아니라 지역이다.</b> 같은 코스의 장소 전부에 같은 값이 붙으므로, 칩 문구와
     * {@link CrowdChip.Basis} 가 그 사실을 드러낸다.
     *
     * <p>표본이 모자라 패턴이 없으면 빈 값이다 — 칩을 띄우지 않는다.
     */
    public Optional<CrowdChip> crowdOn(LocalDate date) {
        if (weekdayPattern == null) {
            return Optional.empty();
        }
        return CrowdChip.ofRegionWeekday(date.getDayOfWeek(), weekdayPattern.factorOf(date.getDayOfWeek()));
    }
}
