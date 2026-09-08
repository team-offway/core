package com.offway.core.transport.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 경유·노선없음으로 넘어갈 때 <b>직통 구간의 흔적을 남기지 않는다</b>(#508).
 *
 * <p>여기 실려 있던 시간표는 직통 구간의 편들이다. 그 구간이 안 다니는 것으로 판명돼 넘어온 참이라,
 * 그대로 두면 "대전복합 경유" 라고 말하면서 직통 시각을 함께 보여주게 된다.
 */
class RegionAccessViaTest {

    private static RegionAccess withDirectDepartures() {
        return RegionAccess.builder()
                .mode(TransitMode.EXPRESS_BUS)
                .status(RegionAccess.Status.POINT_ONLY)
                .fromName("센트럴시티(서울)")
                .toName("무주")
                .departures(List.of(new Departure(
                        "우등",
                        LocalDateTime.of(2026, 10, 15, 8, 0),
                        LocalDateTime.of(2026, 10, 15, 11, 0))))
                .build();
    }

    @Test
    void 경유로_넘어가면_직통_시간표를_버린다() {
        RegionAccess via = withDirectDepartures().withVia("대전복합", 220);

        assertTrue(via.departures().isEmpty(),
                "직통이 안 다녀서 경유로 넘어왔는데 그 구간 시각이 남으면 서로 다른 말을 하게 된다");
        assertEquals("대전복합", via.viaName());
        assertEquals(220, via.durationMinutes());
    }

    @Test
    void 노선이_없으면_시간표와_소요시간을_함께_버린다() {
        RegionAccess none = withDirectDepartures().withDuration(180).withoutRoute();

        assertTrue(none.departures().isEmpty());
        assertNull(none.durationMinutes(), "못 가는 구간에 소요시간이 남으면 갈 수 있는 것으로 읽힌다");
        assertEquals(RegionAccess.Status.NO_ROUTE, none.status());
    }
}
