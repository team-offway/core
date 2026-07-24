package com.offway.core.trip.domain;

/**
 * 인구감소지역 가점 대상 여부. 랭킹 점수에 곱할 가점 배수를 상수별로 들고 있어(다형성) 랭킹이 boolean 분기를 두지 않는다.
 *
 * <p>가점률은 튜닝 전제의 초기 상수다. 현재 추천 후보는 전부 인구감소지역이라 {@link #TARGET} 이지만, 향후 비대상 지역이
 * 후보에 섞이면 {@link #NON_TARGET} 이 쓰인다.
 */
public enum PopulationDeclineStatus {

    /** 인구감소지역(행안부 고시 89) — 가점 대상. */
    TARGET(0.1),

    /** 가점 비대상. */
    NON_TARGET(0.0);

    private final double bonusRate;

    PopulationDeclineStatus(double bonusRate) {
        this.bonusRate = bonusRate;
    }

    /** 랭킹 점수에 이 상태의 가점을 적용한다. */
    public double applyBonus(double score) {
        return score * (1 + bonusRate);
    }
}
