package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Coordinate;
import java.util.List;
import java.util.Set;
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

    @Test
    void 씨앗이_다르면_다른_군집을_고른다() {
        // 재생성의 다양성 축 — 같은 후보 풀에서 다른 코스가 나와야 한다.
        List<Coordinate> points = List.of(
                new Coordinate(37.50, 127.00), new Coordinate(37.51, 127.01), new Coordinate(37.52, 127.02),
                new Coordinate(38.50, 128.00), new Coordinate(38.51, 128.01), new Coordinate(38.52, 128.02));

        List<Integer> fromFirst = GeoCluster.selectCompact(points, 3, 0);
        List<Integer> fromFourth = GeoCluster.selectCompact(points, 3, 3);

        assertNotEquals(Set.copyOf(fromFirst), Set.copyOf(fromFourth), "씨앗이 다르면 다른 곳들이 뽑혀야 한다");
    }

    @Test
    void 씨앗이_달라도_뭉쳐서_고른다() {
        // 다양성을 얻자고 밀집도를 잃으면 안 된다 — 무작위 셔플과 다른 점이다.
        List<Coordinate> seoul = List.of(
                new Coordinate(37.50, 127.00), new Coordinate(37.51, 127.01), new Coordinate(37.52, 127.02));
        List<Coordinate> gangwon = List.of(
                new Coordinate(38.50, 128.00), new Coordinate(38.51, 128.01), new Coordinate(38.52, 128.02));
        List<Coordinate> points = new java.util.ArrayList<>(seoul);
        points.addAll(gangwon);

        // 강원 쪽(인덱스 3)을 씨앗으로 주면 강원끼리 뭉쳐야 한다 — 서울 것이 섞이면 밀집이 깨진 것이다.
        List<Integer> selected = GeoCluster.selectCompact(points, 3, 3);

        assertEquals(Set.of(3, 4, 5), Set.copyOf(selected));
    }

    @Test
    void 같은_씨앗이면_항상_같은_결과다() {
        // 재현성 — 같은 seed 로 문의가 들어오면 같은 코스를 다시 만들어 볼 수 있어야 한다.
        List<Coordinate> points = List.of(
                new Coordinate(37.50, 127.00), new Coordinate(37.55, 127.05), new Coordinate(37.60, 127.10),
                new Coordinate(37.65, 127.15), new Coordinate(37.70, 127.20));

        assertEquals(GeoCluster.selectCompact(points, 3, 2), GeoCluster.selectCompact(points, 3, 2));
    }

    @Test
    void 씨앗이_범위_밖이면_나머지로_감싼다() {
        // 씨앗은 사용자 입력에서 파생되므로 어떤 값이든 들어올 수 있다. 음수도 예외가 아니라 유효한 씨앗이어야 한다.
        List<Coordinate> points = List.of(
                new Coordinate(37.50, 127.00), new Coordinate(37.55, 127.05), new Coordinate(37.60, 127.10));

        assertEquals(GeoCluster.selectCompact(points, 2, 1), GeoCluster.selectCompact(points, 2, 4));
        assertEquals(GeoCluster.selectCompact(points, 2, 1), GeoCluster.selectCompact(points, 2, -2));
    }

    @Test
    void 씨앗_오버로드_없이_부르면_첫_후보를_씨앗으로_쓴다() {
        // 기존 동작 보존 — 재생성이 아닌 첫 생성은 랭킹 상위를 씨앗으로 삼는다.
        List<Coordinate> points = List.of(
                new Coordinate(37.50, 127.00), new Coordinate(37.55, 127.05), new Coordinate(37.60, 127.10));

        assertEquals(GeoCluster.selectCompact(points, 2), GeoCluster.selectCompact(points, 2, 0));
    }
}
