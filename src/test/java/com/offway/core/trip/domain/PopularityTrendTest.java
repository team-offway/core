package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 인기 추세(#394) — "요즘 사람이 늘고 있어요".
 *
 * <p>핵심은 <b>기간 길이가 달라도 흔들리지 않는가</b> 와 <b>재료가 없으면 값을 안 내는가</b> 다.
 */
class PopularityTrendTest {

    /** 비교 하한(28일)을 넘는 관측 일수. 92일은 6~8월 같은 석 달이다. */
    private static final int THREE_MONTHS = 92;

    private static VisitWindow window(double dailyMean, int days) {
        return new VisitWindow(dailyMean * days, days);
    }

    @Test
    void 작년보다_늘었으면_증가율과_상승을_함께_낸다() {
        Optional<PopularityTrend> trend = PopularityTrend.of(
                window(140, THREE_MONTHS), window(100, THREE_MONTHS));

        assertEquals(40, trend.orElseThrow().percent());
        assertTrue(trend.orElseThrow().rising());
    }

    @Test
    void 줄었으면_음수로_내고_상승은_아니다() {
        PopularityTrend trend = PopularityTrend.of(
                window(80, THREE_MONTHS), window(100, THREE_MONTHS)).orElseThrow();

        assertEquals(-20, trend.percent());
        assertFalse(trend.rising());
    }

    /**
     * <b>기간 길이가 증감으로 둔갑하면 안 된다.</b> 합만 견주면 92일과 89일 비교에서 3% 가 그냥 생긴다.
     */
    @Test
    void 관측_일수가_달라도_일평균으로_견준다() {
        PopularityTrend trend = PopularityTrend.of(
                window(100, 92), window(100, 89)).orElseThrow();

        assertEquals(0, trend.percent(), "일평균이 같으면 날 수가 달라도 증감은 0이다");
    }

    @ParameterizedTest
    @CsvSource({
        "101, 1, false", // 0~1%대는 원본이 며칠 빠지는 것만으로 뒤집힌다
        "102, 2, true", // 하한 — 실측 분포에서 정했다(#486)
        "109, 9, true", // 예전 문턱(10%)에 아깝게 걸리던 자리. 실측에서 이런 곳이 둘 있었다
        "140, 40, true", // 시안 예시
    })
    void 상승_하한을_경계에서_가른다(double recentMean, int expectedPercent, boolean rising) {
        PopularityTrend trend = PopularityTrend.of(
                window(recentMean, THREE_MONTHS), window(100, THREE_MONTHS)).orElseThrow();

        assertEquals(expectedPercent, trend.percent());
        assertEquals(rising, trend.rising());
    }

    /**
     * <b>반올림한 값으로 문턱을 넘지 못한다.</b>
     *
     * <p>1.6% 는 표시할 땐 2 이지만 실제로는 하한에 못 미친다. 반올림한 숫자로 판정하면 "늘고
     * 있어요" 가 뜨고, 그 카드를 보고 고른 사용자에게는 우리가 없는 상승을 지어낸 셈이 된다.
     *
     * <p>문턱이 2 로 내려오면서 이 자리가 더 촘촘해졌다(#486) — 실측 분포에 1.5 · 1.6 이 실제로 있다.
     */
    @ParameterizedTest
    @CsvSource({
        "101.6, 2, false", // 표시는 2 이지만 잰 값은 1.6 — 상승이 아니다
        "101.9, 2, false",
        "102.0, 2, true", // 여기서부터 진짜 2%
    })
    void 표시값이_2여도_잰_값이_모자라면_상승이_아니다(
            double recentMean, int expectedPercent, boolean rising) {
        PopularityTrend trend = PopularityTrend.of(
                window(recentMean, THREE_MONTHS), window(100, THREE_MONTHS)).orElseThrow();

        assertEquals(expectedPercent, trend.percent(), "표시용 숫자는 반올림한다");
        assertEquals(rising, trend.rising(), "판정은 잰 값 그대로 본다");
    }

    /** 못 미쳐도 <b>값 자체는 낸다</b> — "재 보니 안 늘었다" 와 "아직 못 잰다" 는 다르다. */
    @Test
    void 상승이_아니어도_추세는_비어_있지_않다() {
        assertTrue(PopularityTrend.of(window(101.6, THREE_MONTHS), window(100, THREE_MONTHS))
                .isPresent());
    }

    /**
     * <b>작년 치가 없으면 값을 내지 않는다.</b> 직전 기간으로 대신하면 계절이 증감으로 둔갑한다 —
     * 여름엔 바다를 낀 지역이 전부 "+40%" 가 된다.
     */
    @Test
    void 작년_표본이_없으면_추세를_내지_않는다() {
        Optional<PopularityTrend> trend =
                PopularityTrend.of(window(140, THREE_MONTHS), new VisitWindow(0, 0));

        assertTrue(trend.isEmpty());
    }

    @Test
    void 최근_표본이_한_달에_못_미치면_내지_않는다() {
        Optional<PopularityTrend> trend =
                PopularityTrend.of(window(140, 27), window(100, THREE_MONTHS));

        assertTrue(trend.isEmpty(), "한 달도 안 되는 표본은 추세가 아니다");
    }

    @Test
    void 작년에_방문자가_0이면_내지_않는다() {
        Optional<PopularityTrend> trend =
                PopularityTrend.of(window(140, THREE_MONTHS), new VisitWindow(0, THREE_MONTHS));

        assertTrue(trend.isEmpty(), "0으로 나누면 증가율이 무한이 된다");
    }
}
