package com.offway.core.trip.domain;

/**
 * 인허가 업종을 코스가 쓰는 분류로 옮긴 것(#144). 각 상수가 자기 {@link PlaceKind} 와 코스 적합도를 함께 안다.
 *
 * <p><b>적합도가 필요한 이유</b> — 인허가 데이터는 전수라 동네 치킨집·호프집까지 들어온다. 의성군 음식점 654건의 업태를 보면
 * 기타 210 · 한식 189 · 호프/통닭 41 처럼 섞여 있어, 그대로 코스에 넣으면 여행 일정에 술집이 배치된다. TourAPI 처럼
 * "관광 콘텐츠로 걸러진" 목록이 아니므로 우리가 순위를 매긴다.
 */
public enum PlaceCategory {

    // ── 숙박 ──
    /** 고택·한옥. 숙박이면서 그 자체로 볼거리라 지방에서 모텔보다 코스 품질이 높다. */
    HANOK(PlaceKind.STAY, "한옥체험", Fitness.PREFERRED),
    TOURIST_HOTEL(PlaceKind.STAY, "관광호텔", Fitness.PREFERRED),
    TOURIST_PENSION(PlaceKind.STAY, "관광펜션", Fitness.PREFERRED),
    RURAL_HOMESTAY(PlaceKind.STAY, "농어촌민박", Fitness.NORMAL),
    CITY_HOMESTAY(PlaceKind.STAY, "도시민박", Fitness.NORMAL),
    /** 여관·모텔. 잘 곳이 없는 것보다는 낫지만 마지막에 채운다. */
    LODGING(PlaceKind.STAY, "숙박업", Fitness.FALLBACK),

    // ── 맛집 ──
    TOURIST_RESTAURANT(PlaceKind.FOOD, "관광식당", Fitness.PREFERRED),
    RESTAURANT(PlaceKind.FOOD, "음식점", Fitness.NORMAL),
    BAKERY(PlaceKind.FOOD, "제과점", Fitness.NORMAL),
    CAFE(PlaceKind.FOOD, "카페", Fitness.FALLBACK),

    // ── 볼거리 ──
    TEMPLE(PlaceKind.SIGHT, "전통사찰", Fitness.PREFERRED),
    MUSEUM(PlaceKind.SIGHT, "박물관·미술관", Fitness.PREFERRED),
    THEME_PARK(PlaceKind.SIGHT, "테마파크", Fitness.PREFERRED),
    CABLE_CAR(PlaceKind.SIGHT, "관광궤도", Fitness.PREFERRED),
    RESORT(PlaceKind.SIGHT, "휴양시설", Fitness.NORMAL),
    CAMPGROUND(PlaceKind.SIGHT, "야영장", Fitness.NORMAL),
    THEATER(PlaceKind.SIGHT, "공연장", Fitness.NORMAL),
    CULTURE_CENTER(PlaceKind.SIGHT, "문화원", Fitness.NORMAL),
    SKI(PlaceKind.SIGHT, "스키장", Fitness.NORMAL),
    /** 골프장은 목적형이라 일반 코스에 끼우면 어색하다. */
    GOLF(PlaceKind.SIGHT, "골프장", Fitness.FALLBACK);

    /** 코스에 넣을 때의 우선순위. 낮은 순서가 먼저 채워진다. */
    public enum Fitness {
        PREFERRED,
        NORMAL,
        FALLBACK
    }

    private final PlaceKind kind;
    private final String label;
    private final Fitness fitness;

    PlaceCategory(PlaceKind kind, String label, Fitness fitness) {
        this.kind = kind;
        this.label = label;
        this.fitness = fitness;
    }

    public PlaceKind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public Fitness fitness() {
        return fitness;
    }

    /** 우선 채울 분류인가 — 같은 지역에 여러 후보가 있으면 이쪽을 먼저 쓴다. */
    public boolean isPreferred() {
        return fitness == Fitness.PREFERRED;
    }
}
