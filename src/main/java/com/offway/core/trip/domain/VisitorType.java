package com.offway.core.trip.domain;

/**
 * 관광빅데이터 방문자 구분(touDivCd). "관광객"은 거주자를 뺀 외지인·외국인으로 본다(방문자≠관광객).
 */
public enum VisitorType {

    /** 현지인(a) — 거주자. 관광객 집계에서 제외. */
    LOCAL("1"),

    /** 외지인(b) — 타지역 방문객. */
    DOMESTIC("2"),

    /** 외국인(c). */
    FOREIGN("3");

    private final String code;

    VisitorType(String code) {
        this.code = code;
    }

    /** 랭킹·한산도 뱃지의 "관광객"에 해당하는가 (외지인·외국인). */
    public boolean isTourist() {
        return this != LOCAL;
    }

    /** touDivCd 문자열을 매핑한다. 알 수 없으면 빈 Optional. */
    public static java.util.Optional<VisitorType> fromCode(String code) {
        for (VisitorType type : values()) {
            if (type.code.equals(code)) {
                return java.util.Optional.of(type);
            }
        }
        return java.util.Optional.empty();
    }
}
