package com.offway.core.trip.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 인구감소지역 랭킹(F3 ③ / course-logic ②). 방문자수를 베이지안으로 보정해 표본이 적은 로컬이 묻히지 않게 하고, 인구감소
 * 가점을 얹어 정렬한다. 혼잡도(crowd)는 점수에 관여하지 않고 뱃지로만 붙는다(랭킹 방향 A).
 *
 * <p>가중치·prior·임계는 전부 튜닝 전제의 초기 상수다(course-logic "파라미터·임계값 확정 시 갱신").
 */
public final class RegionRanking {

    /**
     * 베이지안 prior 강도 — 표본을 이 일수만큼의 글로벌 평균 쪽으로 끌어당긴다. 관측 일수가 적을수록 글로벌 평균에 가까워져
     * 콜드스타트(1~2일 fluke)를 억제한다.
     */
    private static final double PRIOR_STRENGTH = 7.0;

    /** 인구감소지역 가점률 — 동일 방문자면 인구감소지역을 이 비율만큼 위로 올린다. */
    private static final double POPULATION_BONUS_RATE = 0.1;

    private RegionRanking() {
    }

    /** 지역 통계를 받아 점수 내림차순(동점은 regionId 오름차순)으로 랭킹한다. */
    public static List<RegionScore> rank(List<RegionVisitorStat> stats) {
        if (stats.isEmpty()) {
            return List.of();
        }
        double priorMean = pooledMeanDaily(stats);
        return stats.stream()
                .map(stat -> new RegionScore(
                        stat.regionId(), score(stat, priorMean), CrowdLevel.of(stat.meanDaily())))
                .sorted(Comparator.comparingDouble(RegionScore::score)
                        .reversed()
                        .thenComparingLong(RegionScore::regionId))
                .toList();
    }

    /**
     * 글로벌 prior — 전체 방문자 합을 전체 관측일수 합으로 나눈 pooled 일평균. 지역별 일평균을 동일 비중으로 평균내면
     * 1일 관측 지역과 100일 관측 지역이 prior 에 똑같이 반영돼, 소표본 fluke 가 prior 자체를 흔든다. 표본으로
     * 보정한다는 계약에 맞게 관측일수로 가중한다.
     */
    private static double pooledMeanDaily(List<RegionVisitorStat> stats) {
        long totalObservedDays = stats.stream().mapToLong(RegionVisitorStat::observedDays).sum();
        if (totalObservedDays == 0) {
            return 0;
        }
        double totalVisitors = stats.stream().mapToDouble(RegionVisitorStat::touristVisitorsTotal).sum();
        return totalVisitors / totalObservedDays;
    }

    /**
     * 베이지안 보정 일평균에 인구감소 가점을 곱한 점수.
     *
     * <p>보정 일평균 = (총방문자 + prior강도 × 글로벌평균) / (관측일수 + prior강도). 관측일수가 클수록 지역 실측에,
     * 작을수록 글로벌 평균에 가까워진다.
     */
    private static double score(RegionVisitorStat stat, double priorMean) {
        double adjusted = (stat.touristVisitorsTotal() + PRIOR_STRENGTH * priorMean)
                / (stat.observedDays() + PRIOR_STRENGTH);
        double bonus = stat.populationDecline() ? 1 + POPULATION_BONUS_RATE : 1;
        return adjusted * bonus;
    }
}
