package com.offway.core.weather.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SidoNameTest {

    @ParameterizedTest
    @CsvSource({
        "강원특별자치도,강원",
        "전라남도,전남",
        "경상북도,경북",
        "부산광역시,부산",
        "전북특별자치도,전북"
    })
    void 정식_시도명을_에어코리아_축약명으로_바꾼다(String full, String shortName) {
        assertEquals(shortName, SidoName.toAirKorea(full));
    }

    @Test
    void 이미_축약형이거나_모르는_값은_그대로_둔다() {
        assertEquals("강원", SidoName.toAirKorea("강원"));
        assertEquals("알수없음", SidoName.toAirKorea("알수없음"));
    }
}
