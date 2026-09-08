package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 서로 다른 출처의 장소 이름을 맞대는 열쇠(#186).
 *
 * <p>여기서 잠그는 것은 <b>어디까지 지우는가</b> 다. 덜 지우면 같은 가게를 못 맞춰 좌표를 못 얻고,
 * 더 지우면 다른 가게에 남의 좌표가 붙어 코스가 엉뚱한 데를 지난다.
 */
class PlaceNameKeyTest {

    private static String key(String raw) {
        return PlaceNameKey.of(raw).orElseThrow().value();
    }

    /** 연관 관광지가 분류·지점을 슬래시로 덧붙인다 — 인허가 상호에는 그게 없다. */
    @ParameterizedTest
    @CsvSource({
        "동해원/[중식], 동해원",
        "유천냉면/공주점, 유천냉면",
        "베이커리밤마을/[베이커리], 베이커리밤마을",
    })
    void 슬래시_뒤를_버린다(String raw, String expected) {
        assertEquals(expected, key(raw));
    }

    @ParameterizedTest
    @CsvSource({
        "카페 마암, 카페마암",
        "'  곰골식당  ', 곰골식당",
    })
    void 공백을_지운다(String raw, String expected) {
        assertEquals(expected, key(raw));
    }

    @ParameterizedTest
    @CsvSource({
        "(주)한국식당, 한국식당",
        "루치아의뜰(본점), 루치아의뜰",
        "[카페]동학사, 동학사",
    })
    void 괄호와_그_안을_버린다(String raw, String expected) {
        assertEquals(expected, key(raw));
    }

    /** 두 출처가 다르게 적어도 같은 열쇠가 나와야 조인이 된다 — 이게 이 클래스의 목적이다. */
    @Test
    void 표기가_달라도_같은_가게면_같은_열쇠다() {
        assertEquals(key("동해원/[중식]"), key("동해원"));
        assertEquals(key("카페 마암"), key("카페마암"));
        assertEquals(key("유천냉면/공주점"), key("유천냉면"));
    }

    /**
     * <b>지점명을 더 떼지 않는다.</b> 떼면 본점과 2호점이 한 곳으로 접혀, 실제로 다른 가게에 남의
     * 좌표가 붙는다. 못 맞추는 것보다 틀리게 맞추는 쪽이 나쁘다.
     */
    @Test
    void 이름_자체가_다르면_다른_열쇠다() {
        assertNotEquals(key("공주식당본점"), key("공주식당2호점"));
        assertNotEquals(key("동해원"), key("동해루"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "/[중식]", "()"})
    void 맞대_볼_수_없는_이름은_빈_값이다(String raw) {
        assertTrue(PlaceNameKey.of(raw).isEmpty());
    }

    @Test
    void null_도_빈_값이다() {
        assertTrue(PlaceNameKey.of(null).isEmpty());
    }
}
