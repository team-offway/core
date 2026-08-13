package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 중심 관광지 도메인(#185). 대표 사진·볼거리로 쓸지 판단하는 분기가 여기 있다.
 */
class HubAttractionTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    private static HubAttraction.HubAttractionBuilder valid() {
        return HubAttraction.builder()
                .regionId(1L)
                .baseMonth(JUNE)
                .hubRank(1)
                .hubCode("c1")
                .name("공산성")
                .categoryLarge("관광지")
                .categoryMedium("역사관광")
                .lat(36.46)
                .lng(127.12);
    }

    @ParameterizedTest(name = "대분류 {0} → 대표 사진감 {1}")
    @CsvSource({
            "관광지, true",
            // 정선군 1위는 콘도, 2위는 카지노다. 데이터는 맞지만 지역 카드에 걸 그림은 아니다.
            "숙박, false",
            "음식, false",
            "쇼핑, false",
    })
    void 관광지만_대표_사진감이다(String categoryLarge, boolean expected) {
        assertEquals(expected, valid().categoryLarge(categoryLarge).build().isSight());
    }

    @Test
    void 대분류를_모르면_대표_사진감이_아니다() {
        // 알 수 없는 분류를 관광지로 취급하면 카드에 엉뚱한 사진이 걸린다. 모르면 쓰지 않는다.
        assertFalse(valid().categoryLarge(null).build().isSight());
    }

    @Test
    void 좌표가_없으면_스스로_안다() {
        // 좌표가 없으면 슬롯에 넣거나 다른 소스와 이을 수 없다.
        assertTrue(valid().build().hasCoordinate());
        assertFalse(valid().lat(null).build().hasCoordinate());
        assertFalse(valid().lng(null).build().hasCoordinate());
    }

    @Test
    void 기준_연월을_되돌려_준다() {
        // 저장은 문자열이지만 갱신 필요 여부는 달로 비교한다.
        assertEquals(JUNE, valid().build().baseMonth());
    }

    @ParameterizedTest(name = "순위 {0} 은 거부")
    @ValueSource(ints = {0, -1})
    void 순위는_1_이상이어야_한다(int rank) {
        assertThrows(IllegalArgumentException.class, () -> valid().hubRank(rank).build());
    }

    @Test
    void 지역_기준월_식별자_이름은_필수다() {
        assertThrows(NullPointerException.class, () -> valid().regionId(null).build());
        assertThrows(NullPointerException.class, () -> valid().baseMonth(null).build());
        assertThrows(NullPointerException.class, () -> valid().hubCode(null).build());
        assertThrows(NullPointerException.class, () -> valid().name(null).build());
    }

    @Test
    void 빈_이름은_거부한다() {
        // 이름이 비면 화면에 빈 칸이 걸린다 — 없는 것을 넣지 않는다.
        assertThrows(IllegalArgumentException.class, () -> valid().name("  ").build());
        assertThrows(IllegalArgumentException.class, () -> valid().hubCode("").build());
    }
}
