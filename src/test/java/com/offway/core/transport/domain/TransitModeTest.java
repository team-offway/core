package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("타고 내리는 곳 이름")
class TransitModeTest {

    @ParameterizedTest(name = "{0} 로 가면 {1} → {2}")
    @CsvSource({
        "TRAIN,         태안, 태안역",
        "EXPRESS_BUS,   태안, 태안터미널",
        "INTERCITY_BUS, 태안, 태안터미널",
        "FERRY,         부산, 부산여객선터미널",
    })
    void 수단에_맞는_종류를_붙인다(TransitMode mode, String rawName, String expected) {
        assertEquals(expected, mode.placeName(rawName));
    }

    @Test
    void 자차는_지역명을_그대로_쓴다() {
        // 자차의 도착 지점은 역·터미널이 아니라 지역 그 자체다.
        assertEquals("태안", TransitMode.CAR.placeName("태안"));
    }

    @ParameterizedTest(name = "{0} 은 이미 터미널이라 그대로")
    @ValueSource(strings = {"서울고속버스터미널(경부)", "동서울터미널"})
    void 이미_터미널이면_두_번_붙이지_않는다(String rawName) {
        assertEquals(rawName, TransitMode.EXPRESS_BUS.placeName(rawName));
    }

    @ParameterizedTest(name = "{0} 은 이미 타는 곳이라 그대로")
    @ValueSource(strings = {"완도항", "부산_연안부두", "○○선착장", "부산여객선터미널"})
    void 항구_이름에_타는_곳이_들어_있으면_그대로_둔다(String rawName) {
        // 무조건 붙이면 "완도항여객선터미널" 이 된다.
        assertEquals(rawName, TransitMode.FERRY.placeName(rawName));
    }

    @Test
    void 이미_역이면_두_번_붙이지_않는다() {
        assertEquals("서울역", TransitMode.TRAIN.placeName("서울역"));
    }

    @ParameterizedTest(name = "{0} 에도 역을 붙인다")
    @ValueSource(strings = {"역곡", "역삼"})
    void 이름_안에_역이_들어_있어도_끝자리가_아니면_붙인다(String rawName) {
        // contains 로 보면 이런 이름은 영영 "역" 이 안 붙는다 — 끝자리로만 판정한다.
        assertEquals(rawName + "역", TransitMode.TRAIN.placeName(rawName));
    }

    @Test
    void 두_번_불러도_같은_값이다() {
        // RegionAccess 는 withVia 처럼 자기를 다시 만드는 경로가 있어 규칙이 여러 번 걸린다.
        String once = TransitMode.INTERCITY_BUS.placeName("태안");
        assertEquals(once, TransitMode.INTERCITY_BUS.placeName(once));
    }

    @Test
    void 이름이_없으면_지어내지_않는다() {
        assertNull(TransitMode.TRAIN.placeName(null));
        assertEquals("", TransitMode.TRAIN.placeName(""));
        assertEquals("  ", TransitMode.TRAIN.placeName("  "));
    }
}
