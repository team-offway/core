package com.offway.core.trip.domain;

/**
 * 어떤 기간의 관광객 관측 — 합과 관측 일수(#394).
 *
 * <p>합만 들면 기간 길이가 다를 때 비교가 어긋난다. 6~8월은 92일인데 2~4월은 89~90일이라, 합끼리
 * 견주면 <b>날 수 차이가 증감으로 둔갑</b>한다. 그래서 항상 일평균으로 비교한다.
 *
 * @param touristSum 그 기간 관광객 합(거주자 제외)
 * @param observedDays 실제로 값이 있었던 날 수 — 달력상 길이가 아니다. 원본이 며칠 빠져도 평균이
 *     그만큼 낮아지지 않게 한다
 */
public record VisitWindow(double touristSum, int observedDays) {

    /** 비교에 쓸 만한 최소 관측 일수 — 한 달에 못 미치는 표본으로는 추세라 부르지 않는다. */
    private static final int MIN_OBSERVED_DAYS = 28;

    public double dailyMean() {
        return observedDays == 0 ? 0 : touristSum / observedDays;
    }

    /** 이 창으로 추세를 논할 수 있나. */
    public boolean isComparable() {
        return observedDays >= MIN_OBSERVED_DAYS && touristSum > 0;
    }
}
