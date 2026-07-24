package com.offway.core.transport.domain;

/**
 * 이동수단. 두 가지 행위를 자기 상수로 계산한다(분기 없이 다형성):
 *
 * <ul>
 *   <li>{@link #applyReach} — 도달 한계 반경 배율. LNT(가용시간) 산출이 쓴다.
 *   <li>{@link #travelMinutes} — 거리 → 실제 이동시간. 도달시간 계산이 쓴다.
 * </ul>
 *
 * <p>평균속도는 TMAP 실측 전 interim 이다(직선거리 기반 근사). TMAP 어댑터가 붙으면 실제 이동시간이 이 근사를 대체한다.
 */
public enum TransportMode {

    /** 자가용 — 기준. */
    CAR(1.0, 75.0),

    /** 대중교통 — 환승·배차로 자차보다 느려 반경이 줄고 이동시간이 는다. */
    TRANSIT(0.7, 50.0);

    private static final int MINUTES_PER_HOUR = 60;

    private final double reachFactor;
    private final double averageSpeedKmh;

    TransportMode(double reachFactor, double averageSpeedKmh) {
        this.reachFactor = reachFactor;
        this.averageSpeedKmh = averageSpeedKmh;
    }

    /** 기준 반경(분)에 이 수단의 계수를 적용한다. */
    public int applyReach(int baseMinutes) {
        if (baseMinutes < 0) {
            throw new IllegalArgumentException("baseMinutes 는 음수일 수 없습니다: " + baseMinutes);
        }
        return (int) Math.round(baseMinutes * reachFactor);
    }

    /** 이동거리(㎞)를 이 수단의 평균속도로 나눠 이동시간(분)으로 환산한다. */
    public int travelMinutes(double distanceKm) {
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm 은 유한한 음이 아닌 값이어야 합니다: " + distanceKm);
        }
        return (int) Math.round(distanceKm / averageSpeedKmh * MINUTES_PER_HOUR);
    }
}
