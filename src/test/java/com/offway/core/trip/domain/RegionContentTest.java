package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegionContentTest {

    @ParameterizedTest
    @CsvSource({"0,false", "8,false", "9,true", "20,true"})
    void 볼거리가_9개_이상이면_충분하다(int contentCount, boolean sufficient) {
        RegionContent content = new RegionContent(contentCount, null, List.of(), false);
        assertEquals(sufficient, content.isSufficient());
    }

    @Test
    void 인접_병합은_볼거리를_합산하고_categories를_합집합으로_묶고_확장표시를_세운다() {
        RegionContent sparse = new RegionContent(3, "http://a.jpg", List.of(Category.SIGHT), false);
        RegionContent neighbor = new RegionContent(7, "http://b.jpg", List.of(Category.SIGHT, Category.FOOD), false);

        RegionContent merged = sparse.expandedWith(neighbor);

        assertEquals(10, merged.contentCount());
        assertEquals(List.of(Category.SIGHT, Category.FOOD), merged.categories()); // 중복 제거·순서 유지
        assertTrue(merged.neighborIncluded());
        assertTrue(merged.isSufficient());
    }

    @Test
    void 대표이미지가_없으면_인접_이미지로_폴백한다() {
        RegionContent noImage = new RegionContent(2, null, List.of(Category.SIGHT), false);
        RegionContent neighbor = new RegionContent(2, "http://b.jpg", List.of(Category.FOOD), false);

        assertEquals("http://b.jpg", noImage.expandedWith(neighbor).imageUrl());
    }

    @Test
    void 대표이미지가_있으면_인접_이미지로_덮어쓰지_않는다() {
        RegionContent hasImage = new RegionContent(2, "http://a.jpg", List.of(Category.SIGHT), false);
        RegionContent neighbor = new RegionContent(2, "http://b.jpg", List.of(Category.FOOD), false);

        assertEquals("http://a.jpg", hasImage.expandedWith(neighbor).imageUrl());
    }

    @Test
    void 가진_카테고리의_칩에만_걸린다() {
        RegionContent content = new RegionContent(10, null, List.of(Category.SIGHT, Category.FOOD), false);

        assertTrue(content.has(Category.SIGHT));
        assertTrue(content.has(Category.FOOD));
        assertFalse(content.has(Category.STAY));
        assertFalse(content.has(Category.EXPERIENCE));
    }

    /** {@code ALL} 은 필터가 아니라 전체 표지라, 볼거리가 하나도 없는 지역도 목록에서 빠지지 않는다. */
    @Test
    void ALL은_콘텐츠가_비어도_항상_걸린다() {
        assertTrue(RegionContent.EMPTY.has(Category.ALL));
        assertFalse(RegionContent.EMPTY.has(Category.SIGHT));
    }

    @Test
    void 인접이_비어있으면_병합하지_않고_확장표시도_세우지_않는다() {
        RegionContent sparse = new RegionContent(3, "http://a.jpg", List.of(Category.SIGHT), false);

        RegionContent merged = sparse.expandedWith(RegionContent.EMPTY);

        assertSame(sparse, merged);
        assertFalse(merged.neighborIncluded());
    }
}
