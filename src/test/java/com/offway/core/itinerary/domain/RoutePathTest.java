package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("방문 순서 다듬기")
class RoutePathTest {

    /**
     * 한 줄 위에 놓인 지점들 — 좌표 대신 <b>위치 하나</b>로 비용을 만든다.
     *
     * <p>이렇게 두면 최적 순서가 자명해서(왼쪽에서 오른쪽) 무엇이 옳은지 다투지 않아도 된다.
     */
    private static int[][] lineCost(int... positions) {
        int size = positions.length;
        int[][] cost = new int[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                cost[from][to] = Math.abs(positions[from] - positions[to]);
            }
        }
        return cost;
    }

    private static int length(int[][] cost, List<Integer> order) {
        int total = cost[0][order.getFirst()];
        for (int i = 0; i + 1 < order.size(); i++) {
            total += cost[order.get(i)][order.get(i + 1)];
        }
        return total;
    }

    @Test
    void 뒤엉킨_순서를_풀어_짧게_만든다() {
        // 0(출발) 1 2 3 4 가 한 줄에 있는데 2와 3을 뒤바꿔 들른다 — 왔다 갔다 하는 모양이다.
        int[][] cost = lineCost(0, 10, 20, 30, 40);
        List<Integer> tangled = List.of(1, 3, 2, 4);

        List<Integer> improved = RoutePath.improve(cost, tangled);

        assertEquals(List.of(1, 2, 3, 4), improved);
        assertTrue(length(cost, improved) < length(cost, tangled),
                "짧아지지 않았다: " + length(cost, tangled) + " → " + length(cost, improved));
    }

    @Test
    void 최근접이_남긴_먼_구간을_되돌린다() {
        // 최근접은 되돌아오지 않아 마지막에 멀리 튀는 구간을 남긴다. 출발점 바로 옆을 지나쳤다가
        // 끝에서 되돌아오는 모양이 그것이다.
        int[][] cost = lineCost(0, 5, 50, 55, 10);
        List<Integer> greedy = List.of(1, 4, 2, 3);

        List<Integer> improved = RoutePath.improve(cost, greedy);

        assertTrue(length(cost, improved) <= length(cost, greedy));
        assertEquals(List.of(1, 4, 2, 3), improved, "이미 최단이면 그대로 둔다");
    }

    @Test
    void 이미_최단이면_순서를_바꾸지_않는다() {
        // 이득이 0인 교환까지 받아들이면 같은 길이를 오가며 순서만 흔들린다 — 같은 요청이 다른 코스를 낸다.
        int[][] cost = lineCost(0, 10, 20, 30);
        List<Integer> shortest = List.of(1, 2, 3);

        assertEquals(shortest, RoutePath.improve(cost, shortest));
    }

    @Test
    void 두_번_다듬어도_같은_결과다() {
        int[][] cost = lineCost(0, 10, 20, 30, 40);
        List<Integer> once = RoutePath.improve(cost, List.of(1, 3, 2, 4));

        assertEquals(once, RoutePath.improve(cost, once));
    }

    @Test
    void 들를_곳이_둘_이하면_그대로_돌려준다() {
        // 뒤집을 구간이 없다. 여기서 예외가 나면 볼거리가 적은 지역의 코스가 통째로 막힌다.
        int[][] cost = lineCost(0, 10, 20);
        assertEquals(List.of(1, 2), RoutePath.improve(cost, List.of(1, 2)));
        assertEquals(List.of(1), RoutePath.improve(cost, List.of(1)));
        assertEquals(List.of(), RoutePath.improve(cost, List.of()));
    }

    @Test
    void 출발점이_어디냐에_따라_답이_달라진다() {
        // 열린 경로다 — 돌아오지 않으므로 출발점이 순서를 가른다.
        int[][] fromLeft = lineCost(0, 10, 20, 30);
        int[][] fromRight = lineCost(40, 10, 20, 30);

        assertEquals(List.of(1, 2, 3), RoutePath.improve(fromLeft, List.of(1, 2, 3)));
        assertEquals(List.of(3, 2, 1), RoutePath.improve(fromRight, List.of(1, 2, 3)));
    }

    @Test
    void 모든_지점을_한_번씩만_들른다() {
        int[][] cost = lineCost(0, 30, 10, 40, 20);
        List<Integer> improved = RoutePath.improve(cost, List.of(1, 2, 3, 4));

        assertEquals(List.of(1, 2, 3, 4), improved.stream().sorted().toList(),
                "지점이 빠지거나 겹쳤다: " + improved);
    }
}
