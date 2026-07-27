package com.offway.core.weather.domain;

/**
 * 통합대기환경지수(CAI) 등급 — 에어코리아 khaiGrade(1~4)를 의미로 승격한다.
 */
public enum AirGrade {

    GOOD("좋음", 1),
    MODERATE("보통", 2),
    BAD("나쁨", 3),
    VERY_BAD("매우나쁨", 4),
    UNKNOWN("정보없음", 0);

    private final String label;
    private final int level;

    AirGrade(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String label() {
        return label;
    }

    /** 등급 심각도(1~4, UNKNOWN=0). 여러 측정소 중 가장 나쁜 등급을 고를 때 쓴다. */
    public int level() {
        return level;
    }

    /** khaiGrade 코드(1~4) → 등급. 그 외/null 은 UNKNOWN. */
    public static AirGrade fromKhai(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case "1" -> GOOD;
            case "2" -> MODERATE;
            case "3" -> BAD;
            case "4" -> VERY_BAD;
            default -> UNKNOWN;
        };
    }

    /** 더 나쁜(심각도 높은) 등급. */
    public AirGrade worse(AirGrade other) {
        return other.level > this.level ? other : this;
    }
}
