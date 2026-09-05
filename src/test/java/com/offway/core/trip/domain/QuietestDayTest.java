package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 가장 한산한 요일(#394) — 화면에 그대로 나가는 한글 라벨과 불변식. */
class QuietestDayTest {

    /**
     * <b>서버가 한글 라벨을 든다.</b> 로케일을 명시하지 않으면 운영 환경 설정에 따라 "Tuesday" 가 나간다.
     */
    @ParameterizedTest
    @CsvSource({
        "MONDAY, 월요일",
        "TUESDAY, 화요일",
        "SATURDAY, 토요일",
        "SUNDAY, 일요일",
    })
    void 요일을_한글로_내린다(DayOfWeek day, String expected) {
        assertEquals(expected, new QuietestDay(day, 30).label());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 격차가_양수가_아니면_만들_수_없다(int percent) {
        assertThrows(IllegalArgumentException.class, () -> new QuietestDay(DayOfWeek.TUESDAY, percent));
    }
}
