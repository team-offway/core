package com.offway.core.trip.domain;

import java.util.Optional;

/**
 * 그 지역이 <b>요즘 뜨고 있는가</b>(#394) — "추세 +40% · 요즘 사람이 늘고 있어요".
 *
 * <h2>왜 작년 같은 기간과 견주나</h2>
 *
 * <p><b>직전 기간과 견주면 계절이 증감으로 둔갑한다.</b> 6~8월을 3~5월과 비교하면 바다를 낀 지역이
 * 전부 "+40%" 가 되는데, 그건 그 지역이 뜬 것이 아니라 여름이 온 것이다. 반대로 9월에 재면 같은
 * 지역이 전부 "급락" 으로 나온다.
 *
 * <p>작년 같은 기간과 견주면 계절이 양쪽에 똑같이 들어와 상쇄된다. 남는 것이 <b>진짜 증감</b>이다.
 *
 * <h2>재료가 없으면 값을 내지 않는다</h2>
 *
 * <p>작년 치가 없으면 빈 값이다. 계절이 섞인 숫자를 "추세" 라고 내리느니 그 줄을 지우는 편이 낫다 —
 * {@code useTime}·{@code festivalPeriod} 가 이미 그 방식이다.
 */
public record PopularityTrend(int percent, boolean rising) {

    /**
     * "늘고 있다" 고 말하기 위한 최소 증가율 — <b>실측한 분포에서 정한다</b>(#486).
     *
     * <p>처음에는 10 이었다. "한 자릿수는 잡음과 구분이 안 된다" 는 이유였는데, 그건 <b>실제 분포를
     * 보기 전에 정한 값</b>이었다. 운영에서 재 보니 89곳 중 <b>단 1곳</b>만 넘었다 — 화면에 칩이 하나면
     * 그 지표는 없는 것과 같다.
     *
     * <h2>실측 (2026-09-07 · 적재 2025-06~2026-08 · 89곳)</h2>
     *
     * <p>89곳 <b>전부</b> 표본 요건을 채웠다. 모자란 것은 표본이 아니라 문턱이었다.
     *
     * <pre>
     * 변화율 범위  -23.6% ~ +11.1%   평균 -4.4%
     *
     * 문턱   10%    5%    3%    2%    1%
     * 지역     1     4     7     8    12
     * </pre>
     *
     * <p><b>인구감소지역이라 전체가 줄고 있다</b>(평균 -4.4%). 그 분포에서 +10% 는 사실상 닿지 않는
     * 선이었다.
     *
     * <p>2 로 내린다. 그 아래는 값이 0~1%대에 촘촘히 몰려(0.3 · 0.3 · 0.8 · 1.1 · 1.2 …) 원본이 며칠
     * 빠지는 것만으로 순서가 뒤집힌다 — 그때부터는 "상승" 과 "제자리" 를 가르는 선이 흐려진다.
     *
     * <p><b>이 값은 분포가 바뀌면 다시 재야 한다.</b> 적재가 쌓이거나 계절이 바뀌면 같은 2% 가 다른 수의
     * 지역을 뜻하게 된다.
     */
    private static final int MIN_RISING_PERCENT = 2;

    private static final int PERCENT = 100;

    /**
     * 최근과 작년 같은 기간을 견준다. 어느 한쪽이라도 표본이 모자라면 <b>빈 값</b>이다.
     *
     * @param recent 최근 기간
     * @param samePeriodLastYear <b>작년의 같은 달들</b>. 직전 기간을 넘기면 계절이 섞인다(위 참고)
     */
    public static Optional<PopularityTrend> of(VisitWindow recent, VisitWindow samePeriodLastYear) {
        if (!recent.isComparable() || !samePeriodLastYear.isComparable()) {
            return Optional.empty();
        }
        double baseline = samePeriodLastYear.dailyMean();
        double changePercent = (recent.dailyMean() - baseline) / baseline * PERCENT;
        // 반올림한 값으로 판정하지 않는다 — 9.6% 가 10 으로 올라가 "늘고 있다" 가 된다.
        // 표시용 숫자만 반올림하고, 문턱은 잰 값 그대로 넘는지 본다.
        return Optional.of(new PopularityTrend(
                (int) Math.round(changePercent), changePercent >= MIN_RISING_PERCENT));
    }
}
