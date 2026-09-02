package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 연동 현황 조회 결과(#398).
 *
 * <p>기록이 <b>없는</b> 조합을 어떻게 읽느냐가 이 값의 전부다. 저장소는 안 부른 (날짜, API) 에 키를
 * 만들지 않는데, 화면은 0 을 그려야 한다.
 */
class ExternalApiSnapshotTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 9, 2);

    private static ExternalApiSnapshot snapshot() {
        return new ExternalApiSnapshot(
                DAY1,
                DAY2,
                Map.of(
                        DAY1, Map.of(ExternalApi.TOUR_API, 603L, ExternalApi.TOUR_DATA_LAB, 41L),
                        DAY2, Map.of(ExternalApi.TOUR_API, 88L)),
                Map.of(),
                List.of());
    }

    @Test
    void 그날_그_API_호출_수를_돌려준다() {
        assertEquals(603L, snapshot().countOn(DAY1, ExternalApi.TOUR_API));
    }

    /**
     * <b>기록이 없으면 0 이다.</b> 저장소는 안 부른 조합에 키를 만들지 않는데, 화면이 그 빈 칸을 매번
     * 다루면 같은 판단이 여러 곳에 흩어진다.
     */
    @Test
    void 기록이_없는_API_는_0_이다() {
        assertEquals(0L, snapshot().countOn(DAY2, ExternalApi.TOUR_DATA_LAB));
    }

    @Test
    void 기록이_없는_날도_0_이다() {
        assertEquals(0L, snapshot().countOn(LocalDate.of(2026, 8, 31), ExternalApi.TOUR_API));
    }

    @Test
    void 기간_합계는_날짜를_가로질러_더한다() {
        assertEquals(691L, snapshot().total(ExternalApi.TOUR_API));
    }

    /**
     * 한 번도 안 부른 연동도 <b>0 으로 답한다</b>.
     *
     * <p>키가 없다고 목록에서 빼면 "안 쓰는 연동" 이 화면에서 사라진다. 그건 연동 관리 화면이
     * 답해야 할 질문 중 하나다 — 붙여 놓고 안 쓰는 것이 있는가.
     */
    @Test
    void 한_번도_안_부른_연동은_합계가_0_이다() {
        assertEquals(0L, snapshot().total(ExternalApi.BUS_ARRIVAL));
    }
}
