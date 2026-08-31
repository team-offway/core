package com.offway.core.transport.domain;

/**
 * 지역에 닿는 대중교통 수단(#97). 코스가 "무엇을 타고 가서 어디에 내리는가" 를 답할 때 쓴다.
 *
 * <p>{@link TransportMode} 와 다른 축이다. 그쪽은 사용자가 고르는 큰 갈래(자가용·대중교통)고, 여기는 그중
 * 대중교통을 <b>실제로 어느 수단이 실어 나르는가</b> 다. 코스 하나에 둘 다 필요하다 — 사용자는 "대중교통" 을
 * 고르지만 화면에는 "시외버스로 정선터미널 도착" 이 떠야 한다.
 *
 * <p>수단마다 도착 지점이 다르고(역·터미널·항구) 그 지점이 지역 안 동선의 기준점이 된다(#127).
 */
public enum TransitMode {

    /** 열차 — TAGO 열차정보. 유일하게 실제 운행 편·도착 시각까지 안다. */
    TRAIN("열차"),

    /** 고속버스 — {@code ExpBusInfo}. 주요 도시를 잇는다. */
    EXPRESS_BUS("고속버스"),

    /** 시외버스 — {@code SuburbsBusInfo}. 군 단위까지 촘촘히 닿는다. */
    INTERCITY_BUS("시외버스"),

    /** 여객선 — {@code DmstcShipNvgInfo}. 섬 지역엔 이것뿐이다(울릉군·옹진군). */
    FERRY("여객선");

    private final String label;

    TransitMode(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }

    /** 버스 터미널 종류를 그에 대응하는 수단으로 옮긴다 — 터미널 하나가 어느 API 에 속하는지가 곧 수단이다. */
    public static TransitMode of(BusTerminalKind kind) {
        return switch (kind) {
            case EXPRESS -> EXPRESS_BUS;
            case INTERCITY -> INTERCITY_BUS;
        };
    }
}
