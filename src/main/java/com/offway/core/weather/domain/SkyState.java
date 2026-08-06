package com.offway.core.weather.domain;

/**
 * 날씨 상태 — 화면 아이콘 6종에 그대로 대응한다(#135).
 *
 * <p><b>하늘만이 아니라 강수까지 담는다.</b> 기상청은 하늘 상태({@code SKY})와 강수 형태({@code PTY})를 따로
 * 주는데, 화면이 필요한 것은 둘을 합친 하나다. 예전에는 하늘 3종만 내려 <b>비가 와도 "흐림" 으로 나갔다</b> —
 * 데이터가 없어서가 아니라 이미 받고 있는 값을 버리고 있었다.
 *
 * <p><b>강수가 하늘보다 우선한다.</b> 비가 오는데 "구름많음" 으로 안내하면 우산을 안 챙긴다.
 */
public enum SkyState {

    CLEAR("맑음", 0),
    PARTLY_CLOUDY("구름많음", 1),
    CLOUDY("흐림", 2),
    /** 소나기({@code PTY} 4)도 여기로 묶는다 — 화면 아이콘이 6종뿐이라 따로 두지 않는다. */
    RAIN("비", 3),
    SNOW("눈", 4),
    SLEET("눈비", 5),
    UNKNOWN("정보 없음", -1);

    private final String label;

    /**
     * 나쁜 정도 — 하루를 한 줄로 합칠 때 어느 쪽을 대표로 삼을지 정한다.
     *
     * <p>선언 순서(ordinal)가 아니라 값으로 들고 비교한다. 상수를 재배치해도 판정이 안 뒤집힌다.
     */
    private final int severity;

    SkyState(String label, int severity) {
        this.label = label;
        this.severity = severity;
    }

    public String label() {
        return label;
    }

    /** 비·눈·눈비인가 — 강수가 있는 상태. */
    public boolean hasPrecipitation() {
        return this == RAIN || this == SNOW || this == SLEET;
    }

    /**
     * 오전·오후 중 더 나쁜 쪽 — 중기예보를 하루 한 줄로 합칠 때 쓴다(#129).
     *
     * <p><b>좋은 쪽을 대표로 삼으면 실제보다 낫게 안내된다.</b> "오전 맑고 오후 비" 를 "맑음" 으로 뭉개면 사용자가
     * 우산을 안 챙긴다. 모름({@code UNKNOWN})은 비교에서 빠지되, 둘 다 모르면 모름이다.
     */
    public SkyState worseOf(SkyState other) {
        if (other == null || other == UNKNOWN) {
            return this;
        }
        if (this == UNKNOWN) {
            return other;
        }
        return this.severity >= other.severity ? this : other;
    }

    /**
     * 단기예보의 하늘·강수 코드 → 상태(#135).
     *
     * <p>{@code PTY}(강수형태)가 {@code SKY}(하늘)를 덮는다. 강수가 없을 때만 하늘 상태가 답이 된다.
     *
     * @param skyCode {@code SKY} — 1 맑음 · 3 구름많음 · 4 흐림
     * @param precipitationCode {@code PTY} — 0 없음 · 1 비 · 2 비/눈 · 3 눈 · 4 소나기
     */
    public static SkyState from(String skyCode, String precipitationCode) {
        SkyState precipitation = fromPrecipitationCode(precipitationCode);
        return precipitation == UNKNOWN ? fromSkyCode(skyCode) : precipitation;
    }

    /**
     * {@code PTY} 코드 → 강수 상태. 강수가 없거나({@code 0}) 모르는 값이면 {@link #UNKNOWN} 이라, 호출자가
     * 하늘 상태로 넘어가게 한다.
     */
    private static SkyState fromPrecipitationCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code.trim()) {
            case "1", "4" -> RAIN; // 4 = 소나기
            case "2" -> SLEET;
            case "3" -> SNOW;
            default -> UNKNOWN; // "0"(없음) 포함 — 하늘 상태가 답한다
        };
    }

    /** {@code SKY} 코드 → 하늘 상태. 1=맑음·3=구름많음·4=흐림, 그 외/null 은 UNKNOWN. */
    private static SkyState fromSkyCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code.trim()) {
            case "1" -> CLEAR;
            case "3" -> PARTLY_CLOUDY;
            case "4" -> CLOUDY;
            default -> UNKNOWN;
        };
    }

    /**
     * 중기 육상예보의 한글 문구 → 상태(#129·#135). 중기예보는 코드가 아니라 문구로 온다.
     *
     * <p>문구가 {@code "구름많고 비"}·{@code "흐리고 눈"} 처럼 <b>하늘과 강수를 함께</b> 담는다. 예전에는 앞부분
     * (하늘)만 떼고 강수를 버렸다 — 그래서 "흐리고 비" 가 "흐림" 으로 나갔다.
     *
     * <p>강수를 먼저 본다. 뒤쪽에 붙는 형태가 강수이므로 {@code 비/눈} 을 {@code 비}·{@code 눈} 보다 먼저 봐야
     * 눈비가 비로 뭉개지지 않는다.
     */
    public static SkyState fromMidTermText(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = text.trim();
        // 순서가 중요하다 — "비/눈" 이 "비" 를 포함하므로 먼저 걸러낸다.
        if (trimmed.contains("비/눈") || trimmed.contains("눈/비")) {
            return SLEET;
        }
        if (trimmed.contains("소나기") || trimmed.contains("비")) {
            return RAIN;
        }
        if (trimmed.contains("눈")) {
            return SNOW;
        }
        if (trimmed.startsWith("맑음")) {
            return CLEAR;
        }
        // "구름많음"·"구름조금"(코드표의 WB02) 을 함께 받는다. 실측(10구역 110필드)에서는 구름조금이 안 나왔지만,
        // 나오는 순간 UNKNOWN 으로 빠져 하늘 상태가 조용히 사라진다. 구름이 있다는 사실을 살리는 쪽을 택했다.
        if (trimmed.startsWith("구름")) {
            return PARTLY_CLOUDY;
        }
        if (trimmed.startsWith("흐림") || trimmed.startsWith("흐리")) {
            return CLOUDY;
        }
        return UNKNOWN;
    }
}
