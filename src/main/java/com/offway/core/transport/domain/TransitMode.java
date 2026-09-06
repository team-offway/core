package com.offway.core.transport.domain;

/**
 * 코스가 지역까지 타고 가는 수단(#97 · #379). "무엇을 타고 가서 어디에 내리는가" 를 답할 때 쓴다.
 *
 * <p>{@link TransportMode} 와 다른 축이다. 그쪽은 사용자가 고르는 큰 갈래(자가용·대중교통)고, 여기는 그것을
 * <b>실제로 어느 수단이 실어 나르는가</b> 다. 코스 하나에 둘 다 필요하다 — 사용자는 "대중교통" 을 고르지만
 * 화면에는 "시외버스로 정선터미널 도착" 이 떠야 한다.
 *
 * <p><b>이름과 달리 자차도 여기 있다</b>(#379). 처음에는 대중교통만 담았는데, 화면은 자차·기차·버스를
 * <b>같은 카드 한 장</b>으로 그린다. 자차만 다른 타입으로 두면 응답도 그리는 쪽도 둘로 갈리는데, 갈라서
 * 얻는 것이 없다 — 어느 쪽이든 답은 "무엇을 타고 어디에 닿는가" 하나다.
 *
 * <p>수단마다 도착 지점이 다르고(역·터미널·항구, 자차는 지역 자체) 그 지점이 지역 안 동선의 기준점이
 * 된다(#127).
 */
public enum TransitMode {

    /** 열차 — TAGO 열차정보. 유일하게 실제 운행 편·도착 시각까지 안다. */
    TRAIN("열차") {
        @Override
        public int lookaheadDays() {
            throw new IllegalStateException("열차는 구간 소요시간 대상이 아닙니다 — TrainInfoClient 가 시각까지 답합니다.");
        }

        @Override
        public double trunkSpeedKmh() {
            return TRAIN_KMH;
        }
    },

    /** 고속버스 — {@code ExpBusInfo}. 주요 도시를 잇는다. */
    EXPRESS_BUS("고속버스") {
        @Override
        public int lookaheadDays() {
            return BUS_LOOKAHEAD_DAYS;
        }

        @Override
        public double trunkSpeedKmh() {
            return EXPRESS_BUS_KMH;
        }
    },

    /** 시외버스 — {@code SuburbsBusInfo}. 군 단위까지 촘촘히 닿는다. */
    INTERCITY_BUS("시외버스") {
        @Override
        public int lookaheadDays() {
            return BUS_LOOKAHEAD_DAYS;
        }

        @Override
        public double trunkSpeedKmh() {
            return INTERCITY_BUS_KMH;
        }
    },

    /** 여객선 — {@code DmstcShipNvgInfo}. 섬 지역엔 이것뿐이다(울릉군·옹진군). */
    FERRY("여객선") {
        @Override
        public int lookaheadDays() {
            return FERRY_LOOKAHEAD_DAYS;
        }

        @Override
        public double trunkSpeedKmh() {
            return FERRY_KMH;
        }
    },

    /**
     * 자차(#379) — 유일하게 <b>탈 편을 고를 필요가 없는</b> 수단이다.
     *
     * <p>도착 지점이 역·터미널·항구가 아니라 <b>지역 그 자체</b>고, 배차라는 것이 없어 조회창도 구간 측정도
     * 해당하지 않는다. 소요시간은 출발지→지역 이동시간에서 바로 나온다.
     */
    CAR("자차") {
        @Override
        public int lookaheadDays() {
            throw new IllegalStateException("자차는 배차가 없어 조회창이 없습니다.");
        }

        @Override
        public double trunkSpeedKmh() {
            // 자차는 거점을 거치지 않는다 — 간선이라는 구간 자체가 없고, 속도는 TransportMode.CAR 가 든다.
            throw new IllegalStateException("자차는 간선 구간이 없습니다 — TransportMode.CAR 가 답합니다.");
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

    /**
     * 간선 평균속도(㎞/h) — <b>거점과 거점 사이</b>를 이 수단이 실제로 얼마나 빨리 잇는가.
     *
     * <h2>알려진 노선에서 역산했다 (2026-09-06)</h2>
     *
     * <p>직선거리(대권) ÷ 실제 소요시간이다. 도로·선로가 굽는 만큼 유효속도는 표기 최고속도보다 낮게
     * 나오고, 그게 우리가 쓰려는 값이다 — 우리도 직선거리에 곱하기 때문이다.
     *
     * <pre>
     *   열차     서울→부산 KTX 141 · 서울→목포 KTX 125 · 서울→강릉 KTX 90
     *            청량리→안동 무궁화 53 · 서울→영주 무궁화 54      → 중앙 90
     *   고속버스 서울경부→태백 63 · 서울경부→광주 78              → 중앙 71
     *   시외버스 서울남부→홍천 54 · 서울남부→서산 46              → 중앙 50
     *   여객선   포항→울릉 쾌속 63                                 → 63
     * </pre>
     *
     * <p><b>열차는 편차가 크다.</b> KTX(125~141)와 무궁화(53~54)가 갈리는데, 우리 89곳은 KTX 가 안
     * 닿는 곳이 많아 중앙값이 두 극단 사이에 선다. 실측이 있으면 그것을 먼저 쓰므로(구간 소요시간 표)
     * 이 값은 <b>실측이 없는 구간의 추정</b>이다.
     *
     * <p><b>시외버스는 짧은 표본을 뺐다.</b> 동서울→양평(35㎞)이 30㎞/h 로 나오는데, 그 거리에서는
     * 승하차·시내 구간 비중이 커서 간선 속도를 대표하지 못한다. 우리 89곳은 서울에서 대부분 80㎞
     * 이상이다.
     *
     * @throws IllegalStateException 자차인 경우(불변식 — 자차는 거점을 안 거친다)
     */
    public abstract double trunkSpeedKmh();

    /**
     * 간선 거리(㎞)를 이 수단의 소요시간(분)으로 환산한다.
     *
     * <p><b>반올림 결과가 int 를 넘으면 던진다.</b> 지구 둘레가 4만㎞ 라 실제 좌표로는 닿지 않는 값이지만,
     * 넘으면 {@code (int)} 변환이 조용히 음수를 만든다 — 도달시간이 음수면 추천이 그 지역을 "가장 가깝다"
     * 로 읽는다. 입력을 이미 검사하는 메서드라 여기서 끊는 편이 일관된다.
     */
    public int trunkMinutes(double distanceKm) {
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm 은 유한한 음이 아닌 값이어야 합니다: " + distanceKm);
        }
        long minutes = Math.round(distanceKm / trunkSpeedKmh() * MINUTES_PER_HOUR);
        if (minutes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("소요시간이 표현 범위를 넘습니다: " + distanceKm + "km");
        }
        return (int) minutes;
    }

    private static final int MINUTES_PER_HOUR = 60;

    private static final double TRAIN_KMH = 90;
    private static final double EXPRESS_BUS_KMH = 71;
    private static final double INTERCITY_BUS_KMH = 50;
    private static final double FERRY_KMH = 63;

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
