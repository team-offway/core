package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoClusterTest {

    // 서울 근처 밀집 3곳(0·1·2) + 멀리 떨어진 강릉급 아웃라이어(3)
    private static final List<Coordinate> POINTS = List.of(
            new Coordinate(37.50, 127.00),
            new Coordinate(37.51, 127.01),
            new Coordinate(37.49, 126.99),
            new Coordinate(38.50, 128.50));

    @Test
    void 밀집한_곳을_고르고_멀리_떨어진_아웃라이어는_뺀다() {
        List<Integer> selected = GeoCluster.selectCompact(POINTS, 3);

        assertEquals(3, selected.size());
        assertFalse(selected.contains(3)); // 아웃라이어 배제
    }

    @Test
    void 후보가_필요수보다_적으면_전부_고른다() {
        assertEquals(3, GeoCluster.selectCompact(POINTS.subList(0, 3), 5).size());
    }

    @Test
    void nearest는_기준점에_가까운_순으로_고른다() {
        Coordinate anchor = new Coordinate(37.50, 127.00);
        List<Coordinate> pool = List.of(
                new Coordinate(38.50, 128.50), // 0 멀다
                new Coordinate(37.50, 127.01), // 1 가깝다
                new Coordinate(37.80, 127.30)); // 2 중간

        List<Integer> nearest = GeoCluster.nearest(pool, anchor, 2);

        assertEquals(1, nearest.get(0)); // 가장 가까운 게 먼저
        assertFalse(nearest.contains(0)); // 제일 먼 건 제외
    }

    @Test
    void centroid는_좌표_평균이다() {
        Coordinate center = GeoCluster.centroid(List.of(
                new Coordinate(37.0, 127.0), new Coordinate(37.2, 127.4)));

        assertEquals(37.1, center.lat(), 1e-9);
        assertEquals(127.2, center.lng(), 1e-9);
    }

    @Test
    void 아웃라이어가_섞여도_클러스터_평균은_밀집쪽에_가깝다() {
        List<Integer> selected = GeoCluster.selectCompact(POINTS, 3);
        Coordinate center = GeoCluster.centroid(selected.stream().map(POINTS::get).toList());

        assertTrue(center.lat() < 38.0); // 강릉급(38.5)으로 안 끌려감
    }
}
