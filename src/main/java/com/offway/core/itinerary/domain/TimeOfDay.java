package com.offway.core.itinerary.domain;

/**
 * 하루 안의 시간대 슬롯(course-logic ⑥: 오전/점심/오후/저녁). 관광과 식사를 번갈아 배치할 때의 자리.
 */
public enum TimeOfDay {

    MORNING("오전"),
    LUNCH("점심"),
    AFTERNOON("오후"),
    DINNER("저녁");

    private final String label;

    TimeOfDay(String label) {
        this.label = label;
    }

    /** 화면 노출 한글 라벨. */
    public String label() {
        return label;
    }
}
