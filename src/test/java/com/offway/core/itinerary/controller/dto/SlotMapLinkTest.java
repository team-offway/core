package com.offway.core.itinerary.controller.dto;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import org.junit.jupiter.api.Test;

/**
 * 사진 없는 슬롯에 지도 링크(#236).
 *
 * <p>숙소는 89곳 중 45곳에서 사진 있는 후보가 2곳도 안 된다. 인허가 데이터에 사진이 없고, 공식 API 로
 * 숙소 사진을 주는 곳은 유료뿐이다. 사진 없는 카드를 그대로 두는 대신 지도로 넘긴다.
 */
class SlotMapLinkTest {

    private static Slot slot(String imageUrl) {
        return Slot.of(1, TimeOfDay.DINNER, SlotKind.STAY, "LIC-1", "올인모텔", 36.35, 128.69, 0,
                new SlotDisplay(imageUrl, "경상북도 의성군 의성읍 후죽리 1", null, "054-1"));
    }

    @Test
    void 사진이_없으면_지도로_넘긴다() {
        CourseResponse.Item item = CourseResponse.Item.from(slot(null), null, "의성군", null, null);

        assertNotNull(item.mapSearchUrl());
        assertTrue(item.mapSearchUrl().startsWith("https://map.naver.com/p/search/"));
    }

    @Test
    void 사진이_있으면_링크를_안_붙인다() {
        // 카드가 이미 설 수 있어 링크가 군더더기다.
        CourseResponse.Item item =
                CourseResponse.Item.from(slot("http://img/1.jpg"), null, "의성군", null, null);

        assertNull(item.mapSearchUrl());
    }
}
