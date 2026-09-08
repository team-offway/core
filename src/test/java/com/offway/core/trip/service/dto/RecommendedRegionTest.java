package com.offway.core.trip.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.CrowdLevel;
import com.offway.core.trip.domain.PopularityTrend;
import com.offway.core.trip.domain.RegionVisitMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 추천순 재정렬 — <b>무드 → 혜택 → 최근 인기 상승 → 랭킹</b>.
 *
 * <p>순서에 이유가 있다. 무드는 사용자가 직접 고른 조건이고, 혜택은 가면 실제로 받는 것이며, 인기
 * 상승은 우리가 관측으로 얹는 참고값이다. 그 서열이 뒤집히면 사용자가 고른 조건보다 우리 관측이
 * 앞선다.
 */
class RecommendedRegionTest {

    private static final RegionVisitMetrics RISING =
            new RegionVisitMetrics(null, new PopularityTrend(40, true));

    private static final RegionVisitMetrics NOT_RISING =
            new RegionVisitMetrics(null, new PopularityTrend(2, false));

    private static RecommendedRegion region(long id, Category... categories) {
        return region(id, List.of(), RegionVisitMetrics.none(), categories);
    }

    private static RecommendedRegion region(
            long id, List<RecommendedRegion.Benefit> benefits, RegionVisitMetrics metrics,
            Category... categories) {
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
                .benefits(benefits)
                .visitMetrics(metrics)
                .build();
    }

    private static List<RecommendedRegion.Benefit> 혜택() {
        return List.of(new RecommendedRegion.Benefit(1L, "숙박 할인"));
    }

    private static List<Long> ids(List<RecommendedRegion> regions) {
        return regions.stream().map(RecommendedRegion::regionId).toList();
    }

    @Test
    void 무드가_없으면_원본_순서를_그대로_돌려준다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.FOOD));

        assertEquals(List.of(1L, 2L), ids(RecommendedRegion.orderForRecommendation(input, null)));
    }

    @Test
    void 무드가_ALL이면_필터로_보지_않고_원본_순서를_지킨다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.FOOD));

        assertEquals(
                List.of(1L, 2L), ids(RecommendedRegion.orderForRecommendation(input, Category.ALL)));
    }

    @Test
    void 무드_매칭_지역을_앞세우고_그룹내부는_랭킹순을_유지한다() {
        List<RecommendedRegion> input = List.of(
                region(1, Category.SIGHT),
                region(2, Category.FOOD),
                region(3, Category.SIGHT),
                region(4, Category.FOOD));

        List<RecommendedRegion> ordered =
                RecommendedRegion.orderForRecommendation(input, Category.FOOD);

        assertEquals(List.of(2L, 4L, 1L, 3L), ids(ordered)); // FOOD 먼저(2,4), 그다음 나머지(1,3)
    }

    @Test
    void 무드에_매칭되는_지역이_하나도_없으면_원본_순서를_지킨다() {
        List<RecommendedRegion> input = List.of(region(1, Category.SIGHT), region(2, Category.SIGHT));

        assertEquals(
                List.of(1L, 2L), ids(RecommendedRegion.orderForRecommendation(input, Category.FOOD)));
    }

    @Test
    void 혜택_있는_지역을_앞세운다() {
        List<RecommendedRegion> input = List.of(
                region(1, List.of(), RegionVisitMetrics.none()),
                region(2, 혜택(), RegionVisitMetrics.none()));

        assertEquals(List.of(2L, 1L), ids(RecommendedRegion.orderForRecommendation(input, null)));
    }

    @Test
    void 혜택이_같으면_인기_상승한_지역이_앞선다() {
        List<RecommendedRegion> input = List.of(
                region(1, List.of(), NOT_RISING),
                region(2, List.of(), RISING));

        assertEquals(List.of(2L, 1L), ids(RecommendedRegion.orderForRecommendation(input, null)));
    }

    /** <b>혜택이 인기 상승보다 앞선다.</b> 가면 실제로 받는 것이 우리 관측보다 무겁다. */
    @Test
    void 인기_상승보다_혜택이_먼저다() {
        List<RecommendedRegion> input = List.of(
                region(1, List.of(), RISING),
                region(2, 혜택(), RegionVisitMetrics.none()));

        assertEquals(List.of(2L, 1L), ids(RecommendedRegion.orderForRecommendation(input, null)));
    }

    /** <b>사용자가 고른 무드가 가장 앞선다.</b> 우리가 얹는 값이 사용자 선택을 밀어내면 안 된다. */
    @Test
    void 혜택보다_사용자가_고른_무드가_먼저다() {
        List<RecommendedRegion> input = List.of(
                region(1, 혜택(), RISING, Category.SIGHT),
                region(2, List.of(), RegionVisitMetrics.none(), Category.FOOD));

        assertEquals(
                List.of(2L, 1L), ids(RecommendedRegion.orderForRecommendation(input, Category.FOOD)));
    }

    /** 아직 못 재는 지역은 "안 뜬 지역" 과 같이 뒤로 간다 — 없는 것을 상승으로 치지 않는다. */
    @Test
    void 지표가_없는_지역은_상승으로_치지_않는다() {
        List<RecommendedRegion> input = List.of(
                region(1, List.of(), RegionVisitMetrics.none()),
                region(2, List.of(), RISING));

        assertEquals(List.of(2L, 1L), ids(RecommendedRegion.orderForRecommendation(input, null)));
    }
}
