package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 여행 첫날 가용 시간대 판정(#127). 도착 시각 하나로 볼거리·식사 수용량이 갈리는 분기라 여기서 망라한다.
 */
class DayStartTest {

    /** PACKED 밀도의 하루 볼거리 수 — 홀수라 오전·오후 몫이 갈리는 게 드러난다. */
    private static final int SIGHTS_PER_DAY = 3;

    @ParameterizedTest(name = "{0} 도착이면 남는 시간대 {1}개")
    @CsvSource({
            "08:00, 4", // 이른 아침 — 하루 전부
            "11:59, 4", // 오전이 닫히기 직전
            "12:00, 3", // 오전이 닫힌다
            "13:59, 3", // 점심이 닫히기 직전
            "14:00, 2", // 점심이 닫힌다
            "15:00, 2", // 오후 한복판 — 오후는 아직 열려 있다
            "17:59, 2", // 오후가 닫히기 직전
            "18:00, 1", // 오후가 닫힌다
            "20:59, 1", // 저녁이 닫히기 직전
            "21:00, 0", // 밤 도착 — 일정 없음
    })
    void 도착이_늦을수록_남는_시간대가_줄어든다(String arrival, int remaining) {
        assertEquals(remaining, DayStart.arrivingAt(LocalTime.parse(arrival)).usableSlots().size());
    }

    @Test
    void 오후_한복판에_닿아도_오후는_쓴다() {
        // 슬롯은 시점이 아니라 구간이다. 오후 3시 도착을 "오후를 놓쳤다" 로 보면 멀쩡한 반나절을 버린다.
        DayStart afternoon = DayStart.arrivingAt(LocalTime.of(15, 0));

        assertTrue(afternoon.allows(TimeOfDay.AFTERNOON));
        assertFalse(afternoon.allows(TimeOfDay.MORNING));
        assertEquals(1, afternoon.sightCapacity(SIGHTS_PER_DAY), "3곳 중 오후 몫 1곳만 들어간다");
        assertEquals(0, afternoon.morningShare(1), "오전을 못 쓰므로 전부 오후로 간다");
    }

    @Test
    void 저녁까지_지나_닿으면_수용량이_0이다() {
        DayStart night = DayStart.arrivingAt(LocalTime.of(22, 0));

        assertEquals(Set.of(), night.usableSlots());
        assertEquals(0, night.sightCapacity(SIGHTS_PER_DAY));
        assertEquals(0, night.mealCapacity(), "밤에 닿으면 그날 식사 슬롯도 없다(숙박만 남는다)");
    }

    @ParameterizedTest(name = "{0} 도착이면 식사 {1}끼")
    @CsvSource({"08:00, 2", "13:59, 2", "14:00, 1", "20:59, 1", "21:00, 0"})
    void 지나간_끼니는_넣지_않는다(String arrival, int meals) {
        assertEquals(meals, DayStart.arrivingAt(LocalTime.parse(arrival)).mealCapacity());
    }

    @Test
    void 하루_전부면_수용량이_밀도_그대로다() {
        DayStart full = DayStart.fullDay();

        assertEquals(SIGHTS_PER_DAY, full.sightCapacity(SIGHTS_PER_DAY));
        assertEquals(2, full.mealCapacity());
    }

    @ParameterizedTest(name = "볼거리 {0}곳이면 오전 몫 {1}곳")
    @CsvSource({"0, 0", "1, 1", "2, 1", "3, 2", "4, 2", "5, 3"})
    void 하루_전부면_오전이_홀수의_남는_하나를_가진다(int sights, int morning) {
        // 배치 규칙과 같아야 한다 — 여기가 어긋나면 수용량만큼 잘라 놓고 배치에서 넘치거나 남는다.
        assertEquals(morning, DayStart.fullDay().morningShare(sights));
    }

    @Test
    void 일정_없음은_아무_시간대도_허용하지_않는다() {
        DayStart none = DayStart.none();

        for (TimeOfDay slot : TimeOfDay.values()) {
            assertFalse(none.allows(slot), slot + " 이 허용되면 안 된다");
        }
    }

    @ParameterizedTest(name = "{0} 에는 오전을 쓸 수 있다")
    @ValueSource(strings = {"00:00", "06:30", "11:59"})
    void 오전이_닫히기_전에_닿으면_하루를_온전히_쓴다(String arrival) {
        assertTrue(DayStart.arrivingAt(LocalTime.parse(arrival)).allows(TimeOfDay.MORNING));
        assertEquals(SIGHTS_PER_DAY, DayStart.arrivingAt(LocalTime.parse(arrival)).sightCapacity(SIGHTS_PER_DAY));
    }
}
