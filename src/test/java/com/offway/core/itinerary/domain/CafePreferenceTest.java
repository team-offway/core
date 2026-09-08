package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("카페 선택 등급")
class CafePreferenceTest {

    @Test
    void 순위가_먼저다() {
        assertEquals(CafePreference.RELATED, CafePreference.values()[0]);
        assertEquals(CafePreference.PHOTO, CafePreference.values()[1]);
        assertEquals(CafePreference.REST, CafePreference.values()[2]);
    }

    @ParameterizedTest(name = "순위={0} 사진={1} 이면 {2}")
    @CsvSource({
        "true,  true,  RELATED",
        "true,  false, RELATED",
        "false, true,  PHOTO",
        "false, false, REST",
    })
    void 후보는_정확히_한_등급에_속한다(boolean hasRank, boolean hasPhoto, CafePreference expected) {
        // 한 후보가 두 등급에 걸리면 같은 카페가 두 번 뽑힌다.
        assertEquals(1, Arrays.stream(CafePreference.values())
                .filter(tier -> tier.covers(hasRank, hasPhoto))
                .count());
        assertTrue(expected.covers(hasRank, hasPhoto));
    }

    @Test
    void 순위가_있으면_사진_유무로_갈리지_않는다() {
        // 연관 카페는 전부 인허가 장소라 사진이 없다. 사진으로 가르면 이 등급이 통째로 비어
        // 순위가 아무 일도 안 하게 된다.
        assertTrue(CafePreference.RELATED.covers(true, true));
        assertTrue(CafePreference.RELATED.covers(true, false));
    }

    @Test
    void 순위가_있는_등급만_순위로_정렬한다() {
        assertTrue(CafePreference.RELATED.ordersByRank());
        assertFalse(CafePreference.PHOTO.ordersByRank());
        assertFalse(CafePreference.REST.ordersByRank());
    }
}
