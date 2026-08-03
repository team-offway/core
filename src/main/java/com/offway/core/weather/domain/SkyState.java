package com.offway.core.weather.domain;

/**
 * 하늘 상태 — 기상청 단기예보 SKY 코드(1·3·4)를 의미로 승격한다(매직값 대신 enum).
 */
public enum SkyState {

    CLEAR("맑음", 0),
    PARTLY_CLOUDY("구름많음", 1),
    CLOUDY("흐림", 2),
    UNKNOWN("정보 없음", -1);

    private final String label;
    private final int cloudiness;

    SkyState(String label, int cloudiness) {
        this.label = label;
        this.cloudiness = cloudiness;
    }

    public String label() {
        return label;
    }

    /**
     * 오전·오후 중 더 흐린 쪽 — 중기예보를 하루 한 줄로 합칠 때 쓴다(#129).
     *
     * <p><b>맑은 쪽을 대표로 삼으면 실제보다 좋게 안내된다.</b> "오전 맑고 오후 흐림" 을 "맑음" 으로 뭉개면 사용자가
     * 우산을 안 챙긴다. 모름({@code UNKNOWN})은 비교에서 빠지되, 둘 다 모르면 모름이다.
     *
     * <p>흐린 정도를 선언 순서(ordinal)가 아니라 값으로 들고 비교한다 — 상수를 재배치해도 판정이 안 뒤집힌다.
     */
    public SkyState worseOf(SkyState other) {
        if (other == null || other == UNKNOWN) {
            return this;
        }
        if (this == UNKNOWN) {
            return other;
        }
        return this.cloudiness >= other.cloudiness ? this : other;
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

    /**
     * 중기 육상예보의 한글 문구 → 상태(#129). 중기예보는 코드가 아니라 문구로 온다.
     *
     * <p><b>접두사로 가른다.</b> 문구가 "구름많고 비"·"흐리고 눈"처럼 강수를 덧붙인 복합형이라 완전일치로는 못 잡는다.
     * 강수 여부는 별도 강수확률이 답하므로 여기서는 하늘 상태만 본다.
     */
    public static SkyState fromMidTermText(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("맑음")) {
            return CLEAR;
        }
        if (trimmed.startsWith("구름많")) {
            return PARTLY_CLOUDY;
        }
        // "흐림" 과 "흐리고 비" 를 함께 받는다.
        if (trimmed.startsWith("흐림") || trimmed.startsWith("흐리")) {
            return CLOUDY;
        }
        return UNKNOWN;
    }
}
