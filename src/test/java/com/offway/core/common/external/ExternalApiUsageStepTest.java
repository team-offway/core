package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 알림 단계 판정(#257) — <b>같은 단계에서 두 번 울리지 않는가</b>.
 *
 * <p>알림은 단계가 <b>올라가는 순간</b>에만 나간다. 단계 계산이 틀리면 한 번도 안 울리거나(놓침),
 * 호출마다 울린다(소음). 소음 쪽이 더 나쁘다 — 며칠이면 아무도 안 보게 되어 알림이 없는 것과 같아진다.
 */
class ExternalApiUsageStepTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",       // 아직 안 씀
            "1, 0",       // 10% 미만은 단계 없음
            "99, 0",
            "100, 1",     // 10%
            "199, 1",     // 같은 단계 안 — 여기서 또 울리면 안 된다
            "200, 2",
            "999, 9",
            "1000, 10",   // 한도 도달
    })
    void 한도_1000_에서_10퍼센트마다_단계가_오른다(long used, int expected) {
        assertEquals(expected, ExternalApi.TOUR_API.usageStep(used));
    }

    @ParameterizedTest
    @CsvSource({"1000, 10", "1001, 10", "5000, 10"})
    void 한도를_넘겨도_단계는_더_오르지_않는다(long used, int expected) {
        // 초과분마다 단계가 오르면 안 고치는 동안 계속 울린다.
        assertEquals(expected, ExternalApi.TOUR_API.usageStep(used));
    }

    @Test
    void 한도가_작을수록_촘촘하다() {
        // TMAP 경유지 최적화는 50 이라 5건마다 한 단계다. 가장 빡빡한 것을 가장 자주 보게 된다.
        assertEquals(1, ExternalApi.TMAP_WAYPOINT.usageStep(5));
        assertEquals(2, ExternalApi.TMAP_WAYPOINT.usageStep(10));

        // 같은 5건이라도 한도 10,000 짜리는 아직 단계가 없다.
        assertEquals(0, ExternalApi.BUS_STOP.usageStep(5));
    }

    @Test
    void 음수는_단계가_없다() {
        assertEquals(0, ExternalApi.TOUR_API.usageStep(-1));
    }

    @ParameterizedTest
    @CsvSource({"1, 10", "7, 70", "10, 100"})
    void 단계를_퍼센트로_바꾼다(int step, int expected) {
        assertEquals(expected, ExternalApi.percentOf(step));
    }
}
