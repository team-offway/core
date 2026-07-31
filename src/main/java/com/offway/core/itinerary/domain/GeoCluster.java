package com.offway.core.itinerary.domain;

import com.offway.core.transport.domain.Coordinate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 지리 클러스터링(course-logic ⑤) — 흩어진 후보에서 <b>가까운 것끼리 뭉친 집합</b>을 골라 코스가 한 지역에 몰리게 한다. 순서
 * 최적화만으로는 못 줄이는 이동시간을, "애초에 멀리 흩어진 걸 안 고르는" 선택 단계에서 줄인다.
 *
 * <p>순수 지오 계산(외부·프레임워크 무관)이라 단위 테스트로 망라한다.
 */
public final class GeoCluster {

    private GeoCluster() {
    }

    /**
     * 밀집한 {@code count} 곳을 고른 인덱스. 첫 후보(랭킹 상위)를 씨앗으로, 매 단계 현재 선택의 무게중심에 가장 가까운 후보를 붙여
     * 뭉치게 한다 — 외곽 아웃라이어가 자연히 배제된다. {@code count} 가 후보 수 이상이면 전부.
     */
    public static List<Integer> selectCompact(List<Coordinate> points, int count) {
        return selectCompact(points, count, 0);
    }

    /**
     * 씨앗을 골라 밀집 {@code count} 곳을 고른 인덱스 — <b>코스 재생성의 다양성 축</b>(#114).
     *
     * <p>씨앗이 다르면 뭉치는 군집이 달라져 결과가 바뀌지만, "무게중심에 가까운 것을 붙인다" 는 성질은 그대로라
     * <b>밀집도를 잃지 않는다.</b> 후보를 무작위로 섞는 방식과 다른 점이다 — 그쪽은 다양해지는 대신 동선이 망가진다.
     *
     * <p>같은 씨앗이면 항상 같은 결과다. 재생성이 재현 가능해야 문의 대응과 디버깅이 된다.
     *
     * @param seedIndex 시작 후보의 인덱스. 범위 밖이면 후보 수로 나눈 나머지를 쓴다(음수 포함)
     */
    public static List<Integer> selectCompact(List<Coordinate> points, int count, int seedIndex) {
        if (count <= 0 || points.isEmpty()) {
            return List.of();
        }
        if (count >= points.size()) {
            return allIndices(points.size());
        }
        int seed = Math.floorMod(seedIndex, points.size());
        List<Integer> selected = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        selected.add(seed);
        used.add(seed);
        while (selected.size() < count) {
            Coordinate center = centroid(selectedPoints(points, selected));
            int nearest = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < points.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                double distance = center.haversineKmTo(points.get(i));
                if (distance < best) {
                    best = distance;
                    nearest = i;
                }
            }
            selected.add(nearest);
            used.add(nearest);
        }
        return selected;
    }

    /** 기준점(코스 중심)에 가까운 순 {@code count} 곳의 인덱스 — 맛집·숙소를 동선 근처로 고르는 데 쓴다. */
    public static List<Integer> nearest(List<Coordinate> pool, Coordinate anchor, int count) {
        return IntStream.range(0, pool.size())
                .boxed()
                .sorted(Comparator.comparingDouble(i -> anchor.haversineKmTo(pool.get(i))))
                .limit(Math.max(0, count))
                .toList();
    }

    /** 좌표들의 무게중심. */
    public static Coordinate centroid(List<Coordinate> points) {
        double lat = points.stream().mapToDouble(Coordinate::lat).average().orElseThrow();
        double lng = points.stream().mapToDouble(Coordinate::lng).average().orElseThrow();
        return new Coordinate(lat, lng);
    }

    private static List<Coordinate> selectedPoints(List<Coordinate> points, List<Integer> indices) {
        return indices.stream().map(points::get).toList();
    }

    private static List<Integer> allIndices(int size) {
        return IntStream.range(0, size).boxed().toList();
    }
}
