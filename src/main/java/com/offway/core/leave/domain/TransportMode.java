package com.offway.core.leave.domain;

/**
 * 이동수단. 도달 한계 반경을 자기 계수로 조정한다.
 *
 * <p>기준 반경은 {@code 자차}이고, 다른 수단은 그에 대한 배율을 상수로 가진다. 분기(if/switch) 대신 각 상수가 계수를 들고 있어 호출부가
 * 수단을 모른 채 {@code mode.applyReach(base)} 만 호출한다.
 */
public enum TransportMode {

    /** 자가용 — 기준. */
    CAR(1.0),

    /** 대중교통 — 환승·배차로 자차보다 느려 반경이 준다. */
    TRANSIT(0.7);

    private final double reachFactor;

    TransportMode(double reachFactor) {
        this.reachFactor = reachFactor;
    }

    /** 기준 반경(분)에 이 수단의 계수를 적용한다. */
    public int applyReach(int baseMinutes) {
        return (int) Math.round(baseMinutes * reachFactor);
    }
}
