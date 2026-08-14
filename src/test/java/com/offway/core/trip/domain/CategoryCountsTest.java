package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CategoryCountsTest {

    @Test
    void ALL은_콘텐츠가_없는_지역까지_포함한_전체_지역_수다() {
        List<RegionContent> contents = List.of(
                new RegionContent(10, "http://a.jpg", List.of(Category.SIGHT), false),
                new RegionContent(3, null, List.of(Category.FOOD), false),
                RegionContent.EMPTY);

        assertEquals(3, CategoryCounts.of(contents).of(Category.ALL));
    }

    @ParameterizedTest
    @CsvSource({"ALL,3", "SIGHT,2", "FOOD,1", "STAY,0", "EXPERIENCE,0"})
    void 칩마다_그_칩으로_좁혔을_때_나오는_지역_수를_센다(Category category, int expected) {
        List<RegionContent> contents = List.of(
                new RegionContent(10, null, List.of(Category.SIGHT), false),
                new RegionContent(12, null, List.of(Category.SIGHT, Category.FOOD), false),
                RegionContent.EMPTY);

        assertEquals(expected, CategoryCounts.of(contents).of(category));
    }

    /** 한 지역이 여러 칩을 갖고 있어도 칩마다 <b>지역 하나</b>로 센다 — 세는 단위는 볼거리가 아니라 지역이다. */
    @Test
    void 여러_칩을_가진_지역은_칩마다_한_번씩만_센다() {
        List<RegionContent> contents = List.of(
                new RegionContent(30, null, List.of(Category.SIGHT, Category.FOOD, Category.STAY), false));

        CategoryCounts counts = CategoryCounts.of(contents);

        assertEquals(1, counts.of(Category.SIGHT));
        assertEquals(1, counts.of(Category.FOOD));
        assertEquals(1, counts.of(Category.STAY));
        assertEquals(1, counts.of(Category.ALL));
    }

    @Test
    void 지역이_하나도_없으면_전부_0이다() {
        CategoryCounts counts = CategoryCounts.of(List.of());

        assertEquals(0, counts.of(Category.ALL));
        assertEquals(0, counts.of(Category.SIGHT));
    }

    /** 아직 세지 않은 상태를 "전부 0" 으로 읽어도 예외가 나지 않아야 한다 — 부팅 직후 조회가 여기 닿는다. */
    @Test
    void 세지_않은_상태는_모든_칩이_0이다() {
        assertEquals(0, CategoryCounts.EMPTY.of(Category.ALL));
        assertEquals(0, CategoryCounts.EMPTY.of(Category.SIGHT));
    }
}
