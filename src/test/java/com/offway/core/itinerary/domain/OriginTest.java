package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.offway.core.transport.domain.Coordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 출발지의 이름을 가려 받는 규칙(#382).
 *
 * <p>여기서 거절해 버리면 <b>이름 하나 때문에 코스 담기가 통째로 실패한다.</b> 화면 한 줄을 채우는
 * 곁가지 값이라 그건 주객이 뒤집힌 것이다 — 이상하면 이름만 버리고 저장은 진행한다.
 */
class OriginTest {

    private static final Coordinate 서울시청 = new Coordinate(37.5665, 126.9780);

    @Test
    void 좌표와_이름을_함께_든다() {
        Origin origin = Origin.of(서울시청, "서울");

        assertEquals(37.5665, origin.lat());
        assertEquals(126.9780, origin.lng());
        assertEquals("서울", origin.name());
    }

    @Test
    void 좌표_없이는_출발지가_아니다() {
        // 이름만 남으면 도달시간도 거리도 못 재고, 코스가 그 이름으로 할 수 있는 것이 없다.
        assertThrows(NullPointerException.class, () -> Origin.of(null, "서울"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 비어_있는_이름은_없는_것으로_둔다(String name) {
        // 빈 문자열을 그대로 실으면 화면이 "에서 출발" 로 뜬다.
        assertNull(Origin.of(서울시청, name).name());
    }

    @Test
    void 이름이_없어도_출발지는_성립한다() {
        // 지오코딩이 실패했거나 이 필드를 모르는 앱이다. 좌표만으로 도달시간·거리는 그대로 낸다.
        Origin origin = Origin.of(서울시청, null);

        assertNull(origin.name());
        assertEquals(37.5665, origin.lat());
    }

    @Test
    void 상한을_넘는_이름은_거절하지_않고_버린다() {
        // 저장을 막으면 이름 하나 때문에 코스가 안 담긴다. 이름만 버리고 좌표는 남긴다.
        String tooLong = "가".repeat(Origin.MAX_NAME_LENGTH + 1);

        Origin origin = Origin.of(서울시청, tooLong);

        assertNull(origin.name());
        assertEquals(37.5665, origin.lat(), "좌표는 살아 있어야 한다");
    }

    @Test
    void 상한과_같은_길이는_통과한다() {
        // 경계를 배타로 두면 "20자까지" 라고 해 놓고 정확히 20자를 버린다.
        String exact = "가".repeat(Origin.MAX_NAME_LENGTH);

        assertEquals(exact, Origin.of(서울시청, exact).name());
    }

    @Test
    void 앞뒤_공백은_다듬는다() {
        assertEquals("서울", Origin.of(서울시청, "  서울  ").name());
    }
}
