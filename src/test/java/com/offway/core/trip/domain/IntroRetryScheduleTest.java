package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 빈 운영시간을 언제 다시 물을지(#368).
 *
 * <p>여기서 잠그는 것은 <b>비용이 수렴하면서도 포기하지 않는다</b>는 성질이다 — 간격이 자라야 하고,
 * 자라되 멈춰야 한다.
 */
class IntroRetryScheduleTest {

    @ParameterizedTest
    @CsvSource({"1, 7", "2, 14", "3, 28", "4, 56", "5, 112"})
    void 물을수록_간격이_두_배가_된다(int attempts, long expectedDays) {
        assertEquals(Duration.ofDays(expectedDays), IntroRetrySchedule.intervalFor(attempts));
    }

    /**
     * 무한히 늘리면 사실상 포기와 같아진다. 상한이 <b>"원본이 채워지면 늦어도 이 안에는 반영된다"</b> 는
     * 약속이다.
     */
    @ParameterizedTest
    @ValueSource(ints = {6, 7, 10, 30, 100})
    void 아무리_여러_번_비어도_상한에서_멈춘다(int attempts) {
        assertEquals(Duration.ofDays(180), IntroRetrySchedule.intervalFor(attempts));
    }

    /**
     * <b>이게 이 클래스의 존재 이유다.</b> {@code int} 로 배수를 곱하면 스무 번쯤에서 넘쳐 간격이 음수가
     * 되고, 그 순간 그 장소가 매 회차 일감으로 되살아난다 — 줄이려던 비용이 정반대가 된다.
     */
    @ParameterizedTest
    @ValueSource(ints = {40, 60, Integer.MAX_VALUE})
    void 아주_큰_횟수에서도_간격이_음수가_되지_않는다(int attempts) {
        Duration interval = IntroRetrySchedule.intervalFor(attempts);

        assertTrue(interval.isPositive(), "간격=" + interval);
        assertEquals(Duration.ofDays(180), interval);
    }

    @Test
    void 다음_시각은_받은_시각에서_센다() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 8, 31, 3, 0);

        assertEquals(fetchedAt.plusDays(7), IntroRetrySchedule.nextRetryAt(1, fetchedAt));
        assertEquals(fetchedAt.plusDays(14), IntroRetrySchedule.nextRetryAt(2, fetchedAt));
    }

    /** 0회는 "빈 적이 없다" 는 뜻이라 다음 시각이 없다 — 부르는 쪽의 버그다. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 빈_적이_없으면_간격을_묻지_않는다(int attempts) {
        assertThrows(IllegalArgumentException.class, () -> IntroRetrySchedule.intervalFor(attempts));
    }
}
