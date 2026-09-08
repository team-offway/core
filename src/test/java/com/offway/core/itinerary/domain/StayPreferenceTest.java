package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 숙박 후보의 등급 순서(#510).
 *
 * <p>이 순서가 뒤집히면 <b>사진 없는 야영장이 사진 있는 호텔보다 먼저 뽑힌다.</b> 실측에서 그 차이가
 * 사진 있는 숙소 119건과 166건을 갈랐다.
 */
class StayPreferenceTest {

    @ParameterizedTest
    @CsvSource({
            "false, true,  LODGING_WITH_PHOTO",
            "true,  true,  CAMPING_WITH_PHOTO",
            "false, false, LODGING_WITHOUT_PHOTO",
            "true,  false, CAMPING_WITHOUT_PHOTO",
    })
    void 후보는_정확히_한_등급에_든다(boolean camping, boolean hasPhoto, StayPreference expected) {
        StayPreference[] matched = Arrays.stream(StayPreference.values())
                .filter(tier -> tier.covers(camping, hasPhoto))
                .toArray(StayPreference[]::new);

        assertEquals(1, matched.length, "한 후보가 두 등급에 들거나 어디에도 안 들면 채우기가 어긋난다");
        assertEquals(expected, matched[0]);
    }

    /**
     * <b>사진이 첫 축이다.</b> 사진 없는 숙소보다 사진 있는 야영장이 먼저다 — 카드가 서지 않으면
     * 코스에 올라가도 화면이 회색 판이 된다.
     */
    @Test
    void 사진이_숙소_종류보다_앞선다() {
        assertTrue(StayPreference.CAMPING_WITH_PHOTO.ordinal()
                        < StayPreference.LODGING_WITHOUT_PHOTO.ordinal(),
                "사진 없는 숙소가 사진 있는 야영장보다 먼저 오면 카드가 빈다");
    }

    /**
     * <b>같은 사진 조건이면 숙소가 먼저다.</b> 야영장은 잘 수 있는 곳이지만 모두가 텐트에서 자고
     * 싶어 하지는 않는다.
     */
    @Test
    void 같은_조건이면_숙소가_야영장보다_앞선다() {
        assertTrue(StayPreference.LODGING_WITH_PHOTO.ordinal()
                < StayPreference.CAMPING_WITH_PHOTO.ordinal());
        assertTrue(StayPreference.LODGING_WITHOUT_PHOTO.ordinal()
                < StayPreference.CAMPING_WITHOUT_PHOTO.ordinal());
    }
}
