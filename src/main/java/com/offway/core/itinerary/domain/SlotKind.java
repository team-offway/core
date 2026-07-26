package com.offway.core.itinerary.domain;

/**
 * 코스 슬롯이 담는 장소 종류(course-logic ①의 볼거리풀·맛집풀·숙박풀). 관광/맛집이 번갈아 배치되고, 숙박은 멀티데이의 하루 끝에 온다.
 */
public enum SlotKind {

    /** 관광 — 볼거리(자연·역사·문화·레포츠·행사). */
    SIGHT("관광"),

    /** 맛집 — 식사. */
    FOOD("맛집"),

    /** 숙박 — 1박 이상 코스의 잠자리. */
    STAY("숙박");

    private final String label;

    SlotKind(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }
}
