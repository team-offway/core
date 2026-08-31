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
    TRAIN("열차") {
        @Override
        public int lookaheadDays() {
            throw new IllegalStateException("열차는 구간 소요시간 대상이 아닙니다 — TrainInfoClient 가 시각까지 답합니다.");
        }
    },

    /** 고속버스 — {@code ExpBusInfo}. 주요 도시를 잇는다. */
    EXPRESS_BUS("고속버스") {
        @Override
        public int lookaheadDays() {
            return BUS_LOOKAHEAD_DAYS;
        }
    },

    /** 시외버스 — {@code SuburbsBusInfo}. 군 단위까지 촘촘히 닿는다. */
    INTERCITY_BUS("시외버스") {
        @Override
        public int lookaheadDays() {
            return BUS_LOOKAHEAD_DAYS;
        }
    },

    /** 여객선 — {@code DmstcShipNvgInfo}. 섬 지역엔 이것뿐이다(울릉군·옹진군). */
    FERRY("여객선") {
        @Override
        public int lookaheadDays() {
            return FERRY_LOOKAHEAD_DAYS;
        }
    };

    /** 고속·시외버스 배차 조회창 — 오늘~+2일(실측 2026-08-31). 넷째 날은 0건이라 한도만 태운다. */
    private static final int BUS_LOOKAHEAD_DAYS = 3;

    /**
     * 여객선 배차 조회창 — 오늘~+7일(실측 2026-08-31).
     *
     * <p>버스와 같은 3일로 자르면 <b>주 몇 편만 뜨는 항로가 미운행으로 굳는다.</b> 그 항로는 울릉군처럼
     * 배 말고 닿는 수단이 없는 곳의 유일한 길이라, 잘못 굳으면 그 지역이 통째로 "도달 불가" 가 된다.
     */
    private static final int FERRY_LOOKAHEAD_DAYS = 8;

    private final String label;

    TransitMode(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }

    /**
     * 배차를 물을 수 있는 날 수(오늘 포함). 수단마다 다르므로 상수 하나로 묶지 않는다.
     *
     * <p>이 값보다 짧게 물으면 <b>드문 배차가 미운행으로 굳고</b>, 길게 물으면 어차피 0건이라 외부 한도만
     * 태운다. 그래서 "며칠까지 밀어 볼까" 는 호출부가 아니라 수단이 답한다.
     *
     * @throws IllegalStateException 열차인 경우(불변식 — 열차는 이 표를 쓰지 않는다)
     */
    public abstract int lookaheadDays();

    /** 버스 터미널 종류를 그에 대응하는 수단으로 옮긴다 — 터미널 하나가 어느 API 에 속하는지가 곧 수단이다. */
    public static TransitMode of(BusTerminalKind kind) {
        return switch (kind) {
            case EXPRESS -> EXPRESS_BUS;
            case INTERCITY -> INTERCITY_BUS;
        };
    }
}
