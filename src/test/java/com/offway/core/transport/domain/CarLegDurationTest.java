package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 자차 구간 실측값의 불변식(#584). */
class CarLegDurationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 12, 0);

    private static final CoordinateKey FROM = CoordinateKey.of(new Coordinate(37.1, 127.1));

    private static final CoordinateKey TO = CoordinateKey.of(new Coordinate(37.2, 127.2));

    @Test
    void 잰_값을_그대로_들고_있는다() {
        CarLegDuration leg = CarLegDuration.measured(FROM, TO, 42, NOW);

        assertEquals(42, leg.getMinutes());
        assertEquals(FROM.lat(), leg.getFromLat());
        assertEquals(TO.lng(), leg.getToLng());
    }

    /**
     * 0분 이하는 TMAP 이 답할 수 없는 값이다.
     *
     * <p>그대로 저장하면 코스가 <b>"0분 이동"</b> 을 그리고, 그 값이 재측정 주기 내내 굳는다.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 소요시간이_0_이하면_거절한다(int minutes) {
        assertThrows(
                IllegalArgumentException.class, () -> CarLegDuration.measured(FROM, TO, minutes, NOW));
    }

    @Test
    void 재측정_주기가_지나면_다시_잰다() {
        CarLegDuration leg = CarLegDuration.measured(FROM, TO, 42, NOW);

        assertTrue(leg.isFresh(NOW.plusDays(89)));
        assertFalse(leg.isFresh(NOW.plus(CarLegDuration.REMEASURE_AFTER).plusSeconds(1)));
    }
}
