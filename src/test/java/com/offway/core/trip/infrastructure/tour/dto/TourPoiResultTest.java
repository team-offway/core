package com.offway.core.trip.infrastructure.tour.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TourPoiResultTest {

    private static TourPoi poi(String lclsSystm1, String firstImage) {
        return new TourPoi("id", 12, lclsSystm1, "제목", "주소", 34.3, 126.7, firstImage, null);
    }

    @Test
    void 외부응답을_볼거리수_대표이미지_categories로_변환한다() {
        TourPoiResult result = new TourPoiResult(
                List.of(poi("NA", null), poi("FD", "http://x.jpg"), poi("NA", "http://y.jpg")), 38);

        RegionContent content = result.toRegionContent();

        assertEquals(38, content.contentCount()); // 표본이 아니라 totalCount
        assertEquals("http://x.jpg", content.imageUrl()); // 이미지 있는 첫 POI
        assertEquals(List.of(Category.SIGHT, Category.FOOD), content.categories()); // 발견 순서·중복 제거(NA 두 번 → SIGHT 하나)
    }

    @Test
    void 이미지도_매핑가능한_코드도_없으면_이미지는_null이고_categories는_빈다() {
        TourPoiResult result = new TourPoiResult(List.of(poi("ZZ", null)), 5);

        RegionContent content = result.toRegionContent();

        assertNull(content.imageUrl());
        assertEquals(List.of(), content.categories());
        assertEquals(5, content.contentCount());
    }

    @Test
    void 빈_결과는_콘텐츠가_없다() {
        RegionContent content = TourPoiResult.empty().toRegionContent();

        assertEquals(0, content.contentCount());
        assertNull(content.imageUrl());
        assertEquals(List.of(), content.categories());
    }
}
