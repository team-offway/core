package com.offway.core.trip.domain;

import java.util.Arrays;
import java.util.List;

/**
 * 인허가 업종을 화면이 쓰는 분류로 옮긴 것(#144). 각 상수가 자기 {@link PlaceKind} 와 코스 적합도를 함께 안다.
 *
 * <p><b>적합도가 필요한 이유</b> — 인허가 데이터는 전수다. 업태로 술집·편의점을 이미 걷어냈지만, 남은 것끼리도
 * 여행 코스에서의 값어치가 다르다. 한옥·사찰처럼 그 자체가 관광 콘텐츠인 것과 체인 패스트푸드는 같은 무게일 수 없다.
 * 목록에서는 전부 보여주되, 코스가 모자란 자리를 채울 때는 이 순서를 따른다.
 */
public enum PlaceCategory {

    // ── 숙소 ──
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
    /** 한식·숯불구이. 지역 음식을 만날 확률이 가장 높다. */
    KOREAN(PlaceKind.FOOD, "한식", Fitness.PREFERRED),
    SEAFOOD(PlaceKind.FOOD, "횟집·해산물", Fitness.PREFERRED),
    NOODLE(PlaceKind.FOOD, "면·분식", Fitness.NORMAL),
    GLOBAL(PlaceKind.FOOD, "중식·일식·양식", Fitness.NORMAL),
    BUFFET(PlaceKind.FOOD, "뷔페", Fitness.NORMAL),
    /** 업태가 "기타" 로만 적힌 곳. 무엇을 파는지 알 수 없어 앞세우지 않는다. */
    RESTAURANT(PlaceKind.FOOD, "음식점", Fitness.NORMAL),
    FASTFOOD(PlaceKind.FOOD, "패스트푸드", Fitness.FALLBACK),

    // ── 카페 ──
    COFFEE(PlaceKind.CAFE, "커피", Fitness.PREFERRED),
    TRADITIONAL_TEA(PlaceKind.CAFE, "전통찻집", Fitness.PREFERRED),
    BAKERY(PlaceKind.CAFE, "제과점", Fitness.NORMAL),
    DESSERT(PlaceKind.CAFE, "디저트", Fitness.NORMAL),
    TEAROOM(PlaceKind.CAFE, "다방", Fitness.FALLBACK),

    // ── 관광명소 ──
    TEMPLE(PlaceKind.SIGHT, "전통사찰", Fitness.PREFERRED),
    MUSEUM(PlaceKind.SIGHT, "박물관·미술관", Fitness.PREFERRED),
    THEME_PARK(PlaceKind.SIGHT, "테마파크", Fitness.PREFERRED),
    CABLE_CAR(PlaceKind.SIGHT, "케이블카·모노레일", Fitness.PREFERRED),
    RESORT(PlaceKind.SIGHT, "휴양시설", Fitness.NORMAL),
    CAMPGROUND(PlaceKind.SIGHT, "야영장", Fitness.NORMAL),
    THEATER(PlaceKind.SIGHT, "공연장", Fitness.NORMAL),
    CULTURE_CENTER(PlaceKind.SIGHT, "문화원", Fitness.NORMAL),
    SKI(PlaceKind.SIGHT, "스키장", Fitness.NORMAL),
    /** 목적형이라 일반 코스에 끼우면 어색하다. */
    GOLF(PlaceKind.SIGHT, "골프장", Fitness.FALLBACK);

    /** 코스에 넣을 때의 우선순위. 앞선 것부터 채운다. */
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

    /** 이 종류에 속한 분류들(선언 순서 = 적합도 순서). 목록 화면의 필터 칩 재료다. */
    public static List<PlaceCategory> of(PlaceKind kind) {
        return Arrays.stream(values()).filter(category -> category.kind == kind).toList();
    }
}
