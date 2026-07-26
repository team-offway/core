package com.offway.core.itinerary.domain;

/**
 * 일정 밀도(위저드 4/4 "내가 선호하는 스타일은?"). 하루에 몇 곳을 도는지를 스스로 안다 — 코스 생성이 필요 볼거리 수를 이 값으로 계산한다
 * (course-logic ④: 필요 볼거리 = 일수 × 밀도).
 */
public enum Density {

    /** 널널 — 하루 2~3곳, 여유롭게. */
    RELAXED("널널", 3),

    /** 빡빡 — 하루 5~6곳, 알차게. */
    PACKED("빡빡", 6);

    private final String label;
    private final int sightsPerDay;

    Density(String label, int sightsPerDay) {
        this.label = label;
        this.sightsPerDay = sightsPerDay;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }

    /** 하루에 배치할 볼거리 수. */
    public int sightsPerDay() {
        return sightsPerDay;
    }
}
