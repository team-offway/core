package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 경유지 최적 순서를 표에 남기는 규칙(#584).
 *
 * <p>여기서 잠그는 것은 <b>키가 맞는가</b> 하나다. 키가 어긋나면 캐시가 조용히 아무것도 못 찾고,
 * 그러면 한도 50 짜리 API 를 계속 부른다 — <b>캐시를 넣기 전과 똑같은데 아무도 모른다.</b>
 */
class CarRouteOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 12, 0);

    /**
     * <b>순서가 키의 일부다.</b>
     *
     * <p>TMAP 은 첫 점을 출발, 마지막 점을 도착으로 고정하고 가운데만 최적화한다 — 같은 집합이라도
     * 첫·끝이 다르면 결과가 다르다. 순서를 무시하고 키를 만들면 <b>다른 질문에 같은 답을 준다.</b>
     */
    @Test
    void 같은_좌표라도_순서가_다르면_다른_키다() {
        List<Coordinate> one = List.of(point(37.1, 127.1), point(37.2, 127.2), point(37.3, 127.3));
        List<Coordinate> reversed = List.of(point(37.3, 127.3), point(37.2, 127.2), point(37.1, 127.1));

        assertNotEquals(CarRouteOrder.keyOf(one), CarRouteOrder.keyOf(reversed));
    }

    @Test
    void 같은_좌표_같은_순서면_같은_키다() {
        List<Coordinate> one = List.of(point(37.1, 127.1), point(37.2, 127.2));
        List<Coordinate> same = List.of(point(37.1, 127.1), point(37.2, 127.2));

        assertEquals(CarRouteOrder.keyOf(one), CarRouteOrder.keyOf(same));
    }

    /**
     * 자릿수가 달라도 같은 좌표면 같은 키다.
     *
     * <p>{@code CoordinateKey} 가 {@code DECIMAL(10,7)} 로 고정한다 — {@code double} 을 그대로 쓰면
     * 같은 장소를 두 경로로 읽어 마지막 자리가 한 번만 달라져도 다른 키가 되어, <b>저장은 되는데
     * 조회가 영영 안 맞는다.</b>
     */
    @Test
    void 자릿수가_달라도_같은_좌표면_같은_키다() {
        assertEquals(
                CarRouteOrder.keyOf(List.of(point(37.5, 127.0))),
                CarRouteOrder.keyOf(List.of(point(37.50000000, 127.00000000))));
    }

    @Test
    void 저장한_순서를_그대로_되돌린다() {
        List<Coordinate> points = List.of(point(37.1, 127.1), point(37.2, 127.2), point(37.3, 127.3));

        CarRouteOrder saved = CarRouteOrder.measured(points, List.of(0, 2, 1), NOW);

        assertEquals(List.of(0, 2, 1), saved.order());
        assertEquals(CarRouteOrder.keyOf(points), saved.getPoints());
    }

    @Test
    void 재측정_주기가_지나면_다시_잰다() {
        CarRouteOrder saved =
                CarRouteOrder.measured(List.of(point(37.1, 127.1), point(37.2, 127.2)), List.of(0, 1), NOW);

        assertTrue(saved.isFresh(NOW.plusDays(29)));
        assertFalse(saved.isFresh(NOW.plus(CarRouteOrder.REMEASURE_AFTER).plusSeconds(1)));
    }

    /**
     * 점이 최대 12개라 키가 칸을 안 넘는다.
     *
     * <p>넘치면 캐시를 건너뛰게 돼 있지만, <b>실제로는 안 넘친다</b>는 것을 숫자로 잠가 둔다 —
     * 그 경로로 빠지면 한도 50 이 다시 그대로 노출된다.
     */
    @Test
    void 점이_최대일_때도_키가_칸을_안_넘는다() {
        List<Coordinate> twelve = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            // 자릿수를 꽉 채운 좌표로 — 실제보다 긴 최악을 본다.
            twelve.add(point(37.1234567 + i, 127.1234567 + i));
        }

        String key = CarRouteOrder.keyOf(twelve);

        assertTrue(CarRouteOrder.storable(key), "키가 칸을 넘친다 — 길이=" + key.length());
    }

    private static Coordinate point(double lat, double lng) {
        return new Coordinate(lat, lng);
    }
}
