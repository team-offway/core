package com.offway.core.trip.domain;

import java.util.Arrays;

/**
 * TourAPI 콘텐츠 타입(contentTypeId) — 숫자 코드에 의미를 부여한다(매직값 대신 enum). 장소 상세에서 "관광지·음식점" 같은
 * 한글 라벨을 보여줄 때 쓴다.
 */
public enum PoiContentType {

    TOURIST_SPOT(12, "관광지"),
    CULTURE(14, "문화시설"),
    FESTIVAL(15, "축제공연행사"),
    TRAVEL_COURSE(25, "여행코스"),
    LEPORTS(28, "레포츠"),
    STAY(32, "숙박"),
    SHOPPING(38, "쇼핑"),
    RESTAURANT(39, "음식점");

    private static final String UNKNOWN_LABEL = "기타";

    private final int contentTypeId;
    private final String label;

    PoiContentType(int contentTypeId, String label) {
        this.contentTypeId = contentTypeId;
        this.label = label;
    }

    public int contentTypeId() {
        return contentTypeId;
    }

    public String label() {
        return label;
    }

    /** contentTypeId 에 해당하는 한글 라벨. 미지의 코드·null 은 "기타". */
    public static String labelOf(Integer contentTypeId) {
        if (contentTypeId == null) {
            return UNKNOWN_LABEL;
        }
        return Arrays.stream(values())
                .filter(type -> type.contentTypeId == contentTypeId)
                .map(PoiContentType::label)
                .findFirst()
                .orElse(UNKNOWN_LABEL);
    }
}
