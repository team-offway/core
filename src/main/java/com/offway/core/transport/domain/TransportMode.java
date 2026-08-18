package com.offway.core.transport.domain;

/**
 * 이동수단. {@link #travelMinutes} 하나로 자기 성격을 표현한다 — 같은 거리를 몇 분에 가는가.
 *
 * <p><b>도달 한계 분(分)에는 관여하지 않는다</b>(#289). 예전에는 {@code applyReach} 로 반경까지 0.7 을
 * 곱했는데, 분 예산은 <b>이동수단이 아니라 여행이 정하는 값</b>이다 — 대중교통을 탄다고 쓸 수 있는 시간이
 * 줄지는 않는다. 느린 것은 아래 평균속도가 이미 표현하고 있어, 같은 감쇠를 두 번 건 셈이었다.
 *
 * <p>그 결과 대중교통 당일 추천이 서울 출발 기준 89곳 중 3곳까지 줄었다. 배율을 걷으면 5곳,
 * 2박3일은 12곳에서 27곳이 된다.
 *
 * <p>평균속도는 TMAP 실측 전 interim 이다(직선거리 기반 근사). TMAP 어댑터가 붙으면 실제 이동시간이 이 근사를 대체한다.
 */
public enum TransportMode {

    /** 자가용 — 기준. */
    CAR(75.0),

    /** 대중교통 — 환승·배차로 자차보다 느려 같은 거리에 시간이 더 든다. */
    TRANSIT(50.0);

    private static final int MINUTES_PER_HOUR = 60;

    private final double averageSpeedKmh;

    TransportMode(double averageSpeedKmh) {
        this.averageSpeedKmh = averageSpeedKmh;
    }

    /** 이동거리(㎞)를 이 수단의 평균속도로 나눠 이동시간(분)으로 환산한다. */
    public int travelMinutes(double distanceKm) {
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm 은 유한한 음이 아닌 값이어야 합니다: " + distanceKm);
        }
        return (int) Math.round(distanceKm / averageSpeedKmh * MINUTES_PER_HOUR);
    }
}
