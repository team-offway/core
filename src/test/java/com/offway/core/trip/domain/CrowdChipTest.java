package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 혼잡 칩의 경계값(#565).
 *
 * <p>여기가 틀리면 칩이 흔해지거나 사라진다. 실측(2026-09-14 · 12곳 531개 관광지 15,930행) 기준으로
 * 붐빔 80 이상이 12.4%, 한산 20 이하가 14.3% 다 — 그 사이 73% 는 칩이 없다.
 */
class CrowdChipTest {

    @ParameterizedTest
    @ValueSource(doubles = {80.0, 88.0, 99.0, 100.0})
    void 집중률이_80_이상이면_붐빔이다(double rate) {
        Optional<CrowdChip> chip = CrowdChip.ofForecast(rate);

        assertTrue(chip.isPresent());
        assertEquals(CrowdChip.Level.BUSY, chip.get().level());
        assertEquals(CrowdChip.Basis.ATTRACTION_FORECAST, chip.get().basis());
        assertEquals("이날 붐빔", chip.get().label());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 18.0, 20.0})
    void 집중률이_20_이하면_한산이다(double rate) {
        Optional<CrowdChip> chip = CrowdChip.ofForecast(rate);

        assertTrue(chip.isPresent());
        assertEquals(CrowdChip.Level.QUIET, chip.get().level());
        assertEquals("이날 한산", chip.get().label());
    }

    /**
     * <b>문턱 사이는 칩이 없다.</b> 실측 값 분포의 중앙이 35.7 이라 여기가 대부분이다 — 전부 칩을 붙이면
     * "붐빈다" 도 "한산하다" 도 특별한 말이 아니게 된다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {20.1, 35.7, 43.3, 58.8, 79.9})
    void 문턱_사이는_칩을_띄우지_않는다(double rate) {
        assertTrue(CrowdChip.ofForecast(rate).isEmpty());
    }

    /** 가의도 실측 — 같은 장소가 요일과 연휴를 따라 칩이 갈린다. */
    @Test
    void 같은_장소도_날짜에_따라_칩이_갈린다() {
        assertTrue(CrowdChip.ofForecast(43.3).isEmpty(), "수요일 43.3 — 칩 없음");
        assertEquals(CrowdChip.Level.BUSY, CrowdChip.ofForecast(88.0).orElseThrow().level(), "토요일 88");
        assertEquals(CrowdChip.Level.BUSY, CrowdChip.ofForecast(99.0).orElseThrow().level(), "연휴 99");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.4, 1.5, 2.0})
    void 요일계수가_1점4_이상이면_지역이_붐빈다(double factor) {
        Optional<CrowdChip> chip = CrowdChip.ofRegionWeekday(DayOfWeek.SATURDAY, factor);

        assertTrue(chip.isPresent());
        assertEquals(CrowdChip.Level.BUSY, chip.get().level());
        assertEquals(CrowdChip.Basis.REGION_WEEKDAY, chip.get().basis());
    }

    /**
     * <b>1.2 로 내리면 안 되는 이유.</b> 실측에서 토 82곳·일 75곳이 1.2 를 넘는다 — 사실상 주말 코스
     * 전부에 붙어 정보량이 0 이 된다. 1.4 면 토 35곳·일 25곳이다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.81, 0.95, 1.2, 1.32, 1.39})
    void 요일계수가_1점4_미만이면_칩이_없다(double factor) {
        assertTrue(CrowdChip.ofRegionWeekday(DayOfWeek.SATURDAY, factor).isEmpty());
    }

    /**
     * <b>지역 칩은 문구로 지역임이 드러나야 한다.</b> 같은 코스의 장소 전부에 같은 값이 붙으므로,
     * "이날 붐빔" 과 같은 문구를 쓰면 사용자가 장소 속성으로 읽는다.
     */
    @Test
    void 지역_칩은_요일과_지역임을_문구에_담는다() {
        CrowdChip chip = CrowdChip.ofRegionWeekday(DayOfWeek.SATURDAY, 1.4).orElseThrow();

        assertEquals("토요일엔 붐비는 지역", chip.label());
    }

    /** 서버가 한글 라벨을 든다 — 로케일을 명시하지 않으면 운영 환경 설정에 따라 "Saturday" 가 나간다. */
    @Test
    void 요일_라벨은_서버_로케일을_타지_않는다() {
        assertEquals("일요일엔 붐비는 지역",
                CrowdChip.ofRegionWeekday(DayOfWeek.SUNDAY, 1.6).orElseThrow().label());
    }

    /**
     * <b>폴백에는 한산이 없다.</b> 인구감소지역 89곳이 전부 주말에 더 붐벼(주말이 평일보다 한산한 지역
     * 0곳), 대칭으로 쓰면 평일 코스 전체에 "한산" 이 붙는다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.5, 0.81, 0.87})
    void 요일계수가_낮아도_한산_칩은_내지_않는다(double factor) {
        assertTrue(CrowdChip.ofRegionWeekday(DayOfWeek.WEDNESDAY, factor).isEmpty());
    }
}
