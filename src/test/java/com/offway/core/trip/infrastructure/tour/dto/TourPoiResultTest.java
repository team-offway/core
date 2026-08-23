package com.offway.core.trip.infrastructure.tour.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TourPoiResultTest {

    private static final int TOURIST_SPOT = 12;
    private static final int CULTURE = 14;
    private static final int RESTAURANT = 39;
    private static final int STAY = 32;

    private static TourPoi poi(String lclsSystm1, String firstImage) {
        return typed(TOURIST_SPOT, lclsSystm1, firstImage);
    }

    private static TourPoi typed(int contentTypeId, String lclsSystm1, String firstImage) {
        return new TourPoi("id", contentTypeId, lclsSystm1, "제목", "주소", 34.3, 126.7, firstImage, null, null);
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

    @Test
    void 대표사진은_음식점_숙박을_건너뛰고_관광지를_고른다() {
        // 목록에는 숙박·음식점·쇼핑이 섞여 온다. "사진 있는 첫 POI" 를 쓰면 지역 카드에 남의 가게가 걸린다 —
        // 실제로 공주시는 책방, 부산 동구는 횟집이 대표 사진이었다.
        TourPoiResult result = new TourPoiResult(
                List.of(
                        typed(RESTAURANT, "FD", "http://횟집.jpg"),
                        typed(STAY, "AC", "http://펜션.jpg"),
                        typed(TOURIST_SPOT, "NA", "http://폭포.jpg")),
                10);

        assertEquals("http://폭포.jpg", result.toRegionContent().imageUrl());
    }

    @Test
    void 관광지가_없으면_사진_있는_아무_곳이나_쓴다() {
        // 사진을 통째로 비우는 것보다 낫다.
        TourPoiResult result = new TourPoiResult(
                List.of(typed(RESTAURANT, "FD", null), typed(CULTURE, "VE", "http://박물관.jpg")), 10);

        assertEquals("http://박물관.jpg", result.toRegionContent().imageUrl());
    }

    @Test
    void 관광지라도_사진이_없으면_건너뛴다() {
        TourPoiResult result = new TourPoiResult(
                List.of(typed(TOURIST_SPOT, "NA", null), typed(TOURIST_SPOT, "NA", "  "),
                        typed(TOURIST_SPOT, "NA", "http://절.jpg")),
                10);

        assertEquals("http://절.jpg", result.toRegionContent().imageUrl());
    }
}
