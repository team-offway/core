package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 코스가 <b>며칠째가 몇 월 며칠인지</b>를 스스로 안다(#141).
 *
 * <p>화면은 {@code day 1  7.26/토} 처럼 날짜와 요일을 함께 그린다. 프론트가 {@code travelDate + day} 로 더할
 * 수도 있지만, 서버가 이미 그 날짜로 날씨·혜택을 매칭하고 있어 계산 주체가 둘로 갈리면 어긋날 여지가 생긴다.
 */
class DayScheduleDatesTest {

    /** 2026-07-26 은 일요일이다. */
    private static final LocalDate SUNDAY = LocalDate.of(2026, 7, 26);

    @ParameterizedTest
    @CsvSource({"1, 2026-07-26", "2, 2026-07-27", "3, 2026-07-28"})
    void 며칠째인지로_실제_날짜를_구한다(int dayNumber, LocalDate expected) {
        assertEquals(expected, Course.dateOfDay(SUNDAY, dayNumber));
    }

    @Test
    void 여행_시작일이_없으면_날짜도_없다() {
        // 저장 코스 중에는 날짜 없이 저장된 것이 있다(#111 이전). 없는 것을 지어내지 않는다.
        assertNull(Course.dateOfDay(null, 1));
    }

    @Test
    void 달을_넘겨도_이어진다() {
        assertEquals(LocalDate.of(2026, 8, 1), Course.dateOfDay(LocalDate.of(2026, 7, 31), 2));
    }
}
