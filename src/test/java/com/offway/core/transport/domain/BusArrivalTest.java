package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BusArrivalTest {

    /** 남은 시간을 분으로 바꿀 때 올림한다 — 30초 남았는데 "0분"이면 이미 지나간 것처럼 읽힌다. */
    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "30, 1", "59, 1", "60, 1", "61, 2", "180, 3", "181, 4"})
    void 도착까지_남은_분은_올림한다(int seconds, int expectedMinutes) {
        assertEquals(expectedMinutes, new BusArrival("1", "농어촌버스", seconds, 0).arrivalMinutes());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -60})
    void 남은_시간이_음수면_거부한다(int seconds) {
        assertThrows(IllegalArgumentException.class, () -> new BusArrival("1", "농어촌버스", seconds, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5})
    void 남은_정류장_수가_음수면_거부한다(int stopsAway) {
        assertThrows(IllegalArgumentException.class, () -> new BusArrival("1", "농어촌버스", 60, stopsAway));
    }
}
