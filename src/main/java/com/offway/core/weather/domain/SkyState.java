package com.offway.core.weather.domain;

/**
 * 하늘 상태 — 기상청 단기예보 SKY 코드(1·3·4)를 의미로 승격한다(매직값 대신 enum).
 */
public enum SkyState {

    CLEAR("맑음"),
    PARTLY_CLOUDY("구름많음"),
    CLOUDY("흐림"),
    UNKNOWN("정보 없음");

    private final String label;

    SkyState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** SKY 코드 → 상태. 1=맑음·3=구름많음·4=흐림, 그 외/null 은 UNKNOWN. */
    public static SkyState fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case "1" -> CLEAR;
            case "3" -> PARTLY_CLOUDY;
            case "4" -> CLOUDY;
            default -> UNKNOWN;
        };
    }
}
