package com.offway.core.itinerary.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 들를 곳의 <b>순서</b>를 다듬는다(#531) — 외부 호출 없이 우리 손으로.
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>순서를 그리디 최근접(NN) 하나로 정하고 있었다. NN 은 매번 가장 가까운 곳으로 가되 <b>되돌아오지
 * 않아서</b>, 남은 곳이 뒤로 밀리며 마지막에 멀리 튀는 구간이 생긴다. 그걸 다듬는 단계가 <b>자차에만</b>
 * 있었다(TMAP 경유지 최적화). 그 API 는 일일 50회라 대중교통까지 태울 수도 없다 — 코스 하나가
 * 하루치를 다 쓴다.
 *
 * <h2>얼마나 나아지나 (89곳 전수 실측 2026-09-08)</h2>
 *
 * <p>지역 안 이동(볼거리 12곳 사이) 중앙값이 <b>26.5㎞ → 24.0㎞</b> 로 준다.
 *
 * <ul>
 *   <li>단축률 <b>중앙값 3.7% · 평균 7.1% · 최대 35.6%</b>
 *   <li><b>42/89곳</b>이 5% 넘게 준다
 * </ul>
 *
 * <p>전체 경로(출발지 포함)로 재면 200㎞ 짜리 접근 구간이 분모를 지배해 0.3% 로 희석된다. 재배열이
 * 바꿀 수 있는 것은 지역 안 구간이라 그쪽 숫자를 쓴다.
 *
 * <h2>비용이 대칭이어야 한다</h2>
 *
 * <p>2-opt 는 구간을 <b>통째로 뒤집어</b> 이득을 본다. 뒤집힌 안쪽 비용이 그대로라고 볼 수 있어야
 * 바깥 두 구간만 견주면 되는데, 그것이 성립하려면 {@code cost[a][b] == cost[b][a]} 여야 한다.
 *
 * <p>우리 추정(직선거리 × 보정 ÷ 속도)은 대칭이라 성립한다. <b>TMAP 실도로에는 얹지 않는다</b> —
 * 일방통행·회전 제한 때문에 편도마다 값이 다를 수 있다.
 *
 * <h2>여기서 외부를 부르지 않는다</h2>
 *
 * <p>거리 행렬을 <b>인자로 받는다.</b> 만드는 것은 호출자의 일이고, 한 번 만들어 두면 이 안의 반복은
 * 전부 배열 읽기다. 이 클래스가 provider 를 들면 반복마다 호출이 나가 최적화가 곧 비용이 된다.
 */
public final class RoutePath {

    /**
     * 개선을 멈출 최대 왕복 수 — 안전판이다.
     *
     * <p>2-opt 는 이득이 없을 때 스스로 멈추므로 보통 몇 왕복이면 끝난다. 그래도 상한을 두는 이유는
     * 부동소수점이 아니라 <b>정수 분</b>으로 견주기 때문이다 — 이득이 0인 교환을 두 값이 같다고 보고
     * 계속 맞바꾸는 상황을 원천적으로 막는다(아래 {@code gain > 0} 조건과 함께).
     */
    private static final int MAX_PASSES = 50;

    /** 뒤집을 구간이 성립하는 최소 개수. 둘 이하는 순서를 바꿔도 같은 경로다. */
    private static final int MIN_STOPS = 3;

    private RoutePath() {
    }

    /**
     * 순서를 다듬는다.
     *
     * @param cost 0 번이 <b>출발점</b>, 1..n 이 들를 곳인 대칭 비용 행렬
     * @param order 들를 곳의 현재 순서(1..n 의 순열). 보통 최근접 정렬의 결과다
     * @return 더 짧아진 순서. 개선할 것이 없으면 받은 순서 그대로
     */
    public static List<Integer> improve(int[][] cost, List<Integer> order) {
        if (cost == null || order == null || order.size() < MIN_STOPS) {
            return order == null ? List.of() : List.copyOf(order);
        }
        int[] best = order.stream().mapToInt(Integer::intValue).toArray();
        boolean improved = true;
        int passes = 0;
        while (improved && passes < MAX_PASSES) {
            improved = false;
            passes++;
            for (int i = 0; i < best.length - 1; i++) {
                for (int j = i + 1; j < best.length; j++) {
                    // **이득이 있을 때만 뒤집는다.** 0 이면 그대로 둔다 — 같은 길이를 오가며
                    // 순서만 뒤집히면 같은 요청이 다른 코스를 낸다.
                    if (gainOfReversing(cost, best, i, j) > 0) {
                        reverse(best, i, j);
                        improved = true;
                    }
                }
            }
        }
        List<Integer> result = new ArrayList<>(best.length);
        for (int stop : best) {
            result.add(stop);
        }
        return List.copyOf(result);
    }

    /**
     * {@code [i..j]} 구간을 뒤집으면 얼마나 짧아지나(양수면 이득).
     *
     * <p>안쪽은 뒤집어도 합이 같으므로(대칭) <b>바깥에 맞닿는 두 구간만</b> 견준다.
     *
     * <p>앞이 없으면 출발점(0번)이 앞이고, 뒤가 없으면 그쪽 구간은 애초에 없다 — 열린 경로라 마지막
     * 지점에서 어디로도 돌아가지 않는다.
     */
    private static int gainOfReversing(int[][] cost, int[] order, int i, int j) {
        int before = i == 0 ? 0 : order[i - 1];
        int gain = cost[before][order[i]] - cost[before][order[j]];
        if (j + 1 < order.length) {
            int after = order[j + 1];
            gain += cost[order[j]][after] - cost[order[i]][after];
        }
        return gain;
    }

    private static void reverse(int[] order, int from, int to) {
        for (int left = from, right = to; left < right; left++, right--) {
            int swap = order[left];
            order[left] = order[right];
            order[right] = swap;
        }
    }
}
