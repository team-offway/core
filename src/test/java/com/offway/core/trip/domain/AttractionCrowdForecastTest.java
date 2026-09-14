package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 엔티티가 스스로 지키는 불변식(#565) — <b>최후의 보루</b>다.
 *
 * <p>클라이언트가 깨진 값을 이미 걸러 내지만, 엔티티는 누가 만들든 유효함을 보장해야 한다.
 */
class AttractionCrowdForecastTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 9, 14, 5, 10);

    private static AttractionCrowdForecast.AttractionCrowdForecastBuilder 기본() {
        return AttractionCrowdForecast.builder()
                .regionId(1L)
                .attractionName("가의도")
                .baseDate(LocalDate.of(2026, 9, 14))
                .fetchedAt(FETCHED);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 45.17, 100.0})
    void 집중률은_0에서_100_사이다(double rate) {
        assertEquals(rate, 기본().rate(rate).build().getRate());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 100.1, 1000.0})
    void 범위_밖이면_거절한다(double rate) {
        assertThrows(IllegalArgumentException.class, () -> 기본().rate(rate).build());
    }

    /**
     * <b>NaN 은 범위 검사를 빠져나간다.</b> 어떤 비교에도 거짓이라 {@code value < 0 || value > 100} 이
     * 통과시킨다. 그대로 두면 MySQL {@code DOUBLE} 까지 가는데, 거기서 보존된다는 보장이 없어 저장
     * 시점에 바뀌거나 거절된다 — 저장 결과에 기대지 않고 여기서 막는다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않으면_거절한다(double rate) {
        assertThrows(IllegalArgumentException.class, () -> 기본().rate(rate).build());
    }

    @Test
    void 관광지명이_비면_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> 기본().attractionName(" ").rate(50).build());
    }

    @Test
    void 날짜가_없으면_거절한다() {
        assertThrows(NullPointerException.class, () -> 기본().baseDate(null).rate(50).build());
    }

    /** 이름은 앞뒤 공백을 털어 저장한다 — 우리 장소명과 맞추는 키라 공백 하나로 안 맞으면 칩이 사라진다. */
    @Test
    void 관광지명의_앞뒤_공백을_턴다() {
        assertEquals("가의도", 기본().attractionName("  가의도  ").rate(50).build().getAttractionName());
    }
}
