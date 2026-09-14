package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.CrowdChip;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 장소별 값과 지역 폴백 중 무엇을 내는가(#565).
 *
 * <p><b>폴백은 "모르는 장소" 에만 쓴다.</b> 재어 놓고도 더 거친 지역 값으로 덮으면 그 장소에 대해
 * 조용히 틀린 말을 하게 된다 — "집중률 50" 인 곳에 "토요일엔 붐비는 지역" 이 붙는 자리다.
 */
class CourseCrowdTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 19);
    private static final String PLACE = "가의도";

    private static CrowdChip 지역붐빔() {
        return CrowdChip.ofRegionWeekday(DayOfWeek.SATURDAY, 1.6).orElseThrow();
    }

    private static CourseCrowd.Key key(String place) {
        return new CourseCrowd.Key(place, SATURDAY);
    }

    @Test
    void 장소별_칩이_있으면_그것을_낸다() {
        CrowdChip forPlace = CrowdChip.ofForecast(95.0).orElseThrow();
        CourseCrowd crowd = new CourseCrowd(
                Map.of(key(PLACE), forPlace), Set.of(key(PLACE)), Map.of(SATURDAY, 지역붐빔()));

        assertEquals(CrowdChip.Basis.ATTRACTION_FORECAST, crowd.of(PLACE, SATURDAY).orElseThrow().basis());
    }

    /**
     * <b>이 PR 의 핵심 분기.</b> 예보가 있고 값이 문턱 사이면 칩이 없어야 하고, 지역 폴백으로
     * 내려가서도 안 된다.
     */
    @Test
    void 예보가_있고_값이_문턱_사이면_폴백을_타지_않는다() {
        // 집중률 50 — ofForecast 가 빈 값이라 byPlace 에는 안 들어가지만 measured 에는 있다.
        CourseCrowd crowd = new CourseCrowd(
                Map.of(), Set.of(key(PLACE)), Map.of(SATURDAY, 지역붐빔()));

        assertTrue(crowd.of(PLACE, SATURDAY).isEmpty(),
                "재어 보고 보통이었는데 지역 값으로 덮으면 그 장소에 대해 틀린 말을 한다");
    }

    @Test
    void 예보가_없는_장소만_지역_폴백을_탄다() {
        CourseCrowd crowd = new CourseCrowd(
                Map.of(), Set.of(key("이름이맞는곳")), Map.of(SATURDAY, 지역붐빔()));

        CrowdChip chip = crowd.of("예보없는곳", SATURDAY).orElseThrow();

        assertEquals(CrowdChip.Basis.REGION_WEEKDAY, chip.basis());
    }

    /** 폴백도 없으면 칩이 없다 — "확인 안 됨" 을 띄우지 않는다. */
    @Test
    void 둘_다_없으면_칩이_없다() {
        CourseCrowd crowd = new CourseCrowd(Map.of(), Set.of(), Map.of());

        assertTrue(crowd.of(PLACE, SATURDAY).isEmpty());
    }

    /** 폴백은 날짜 단위다 — 다른 날짜를 끌어 쓰지 않는다. */
    @Test
    void 다른_날짜의_폴백을_끌어_쓰지_않는다() {
        CourseCrowd crowd = new CourseCrowd(Map.of(), Set.of(), Map.of(SATURDAY, 지역붐빔()));

        assertTrue(crowd.of(PLACE, SATURDAY.plusDays(1)).isEmpty());
    }

    /** 교통 거점 칸은 장소명이 있어도 날짜가 없을 수 있다 — NPE 로 코스를 깨지 않는다. */
    @Test
    void 장소명이나_날짜가_없으면_칩이_없다() {
        CourseCrowd crowd = new CourseCrowd(Map.of(), Set.of(), Map.of(SATURDAY, 지역붐빔()));

        assertTrue(crowd.of(null, SATURDAY).isEmpty());
        assertTrue(crowd.of(PLACE, null).isEmpty());
    }

    @Test
    void 빈_묶음은_아무것도_내지_않는다() {
        assertTrue(CourseCrowd.empty().of(PLACE, SATURDAY).isEmpty());
    }
}
