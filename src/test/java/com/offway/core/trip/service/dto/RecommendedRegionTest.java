package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.offway.core.transport.domain.Coordinate;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendedRegionTest {

    private static RecommendedRegion region(long id, Category... categories) {
        return RecommendedRegion.builder()
                .regionId(id)
                .sido("시도")
                .sigungu("시군구" + id)
                .coordinate(new Coordinate(37.4, 128.6))
                .reachMinutes(60)
                .crowdLevel(CrowdLevel.LOW)
                .contentCount(10)
                .categories(List.of(categories))
                .neighborIncluded(false)
                .benefits(List.of())
                .build();
    }

    private static List<Long> ids(List<RecommendedRegion> regions) {
        return regions.stream().map(RecommendedRegion::regionId).toList();
    }

    @Test
    void 무드가_없으면_원본_순서를_그대로_돌려준다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.FOOD));

        assertSame(input, RecommendedRegion.orderByMood(input, null));
    }

    @Test
    void 무드가_ALL이면_필터로_보지_않고_원본_순서를_지킨다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.FOOD));

        assertSame(input, RecommendedRegion.orderByMood(input, Category.ALL));
    }

    @Test
    void 무드_매칭_지역을_앞세우고_그룹내부는_랭킹순을_유지한다() {
        List<RecommendedRegion> input = List.of(
                region(1, Category.SIGHT),
                region(2, Category.FOOD),
                region(3, Category.SIGHT),
                region(4, Category.FOOD));

        List<RecommendedRegion> ordered = RecommendedRegion.orderByMood(input, Category.FOOD);

        assertEquals(List.of(2L, 4L, 1L, 3L), ids(ordered)); // FOOD 먼저(2,4), 그다음 나머지(1,3) — 각 그룹 안은 원래 순서
    }

    @Test
    void 무드에_매칭되는_지역이_하나도_없으면_원본_순서를_지킨다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.SIGHT));

        assertSame(input, RecommendedRegion.orderByMood(input, Category.FOOD));
    }
}
