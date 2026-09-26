package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 지금 어느 키로 도는가(#596).
 *
 * <p>이 상태가 있는 이유는 <b>마른 키를 매 요청 다시 두드리지 않기</b> 위해서다. 없으면 호출도 지연도
 * 두 배가 되고, 주 키 집계는 100% 를 넘어 계속 오른다.
 */
class ExternalKeyStateTest {

    @Test
    void 기본은_주_키다() {
        ExternalKeyState state = new ExternalKeyState();

        assertFalse(state.usingFallback(ExternalApi.TMAP_WAYPOINT));
        assertEquals("주 키", state.label(ExternalApi.TMAP_WAYPOINT));
    }

    @Test
    void 말랐다고_표시하면_보조_키로_돈다() {
        ExternalKeyState state = new ExternalKeyState();

        state.markPrimaryExhausted(ExternalApi.TMAP_WAYPOINT);

        assertTrue(state.usingFallback(ExternalApi.TMAP_WAYPOINT));
        assertEquals("보조 키", state.label(ExternalApi.TMAP_WAYPOINT));
    }

    /**
     * <b>API 마다 따로 기억한다.</b>
     *
     * <p>경유지 최적화(50)가 말라도 경로 탐색(1,000)은 멀쩡하다. 한 덩어리로 묶으면 멀쩡한 쪽까지
     * 보조 키를 쓰게 되어, 정작 필요한 순간에 보조 키 한도가 닳아 있다.
     */
    @Test
    void API_마다_따로_기억한다() {
        ExternalKeyState state = new ExternalKeyState();

        state.markPrimaryExhausted(ExternalApi.TMAP_WAYPOINT);

        assertTrue(state.usingFallback(ExternalApi.TMAP_WAYPOINT));
        assertFalse(state.usingFallback(ExternalApi.TMAP_ROUTE), "경로 탐색은 한도가 다르다");
        assertFalse(state.usingFallback(ExternalApi.TOUR_API));
    }
}
