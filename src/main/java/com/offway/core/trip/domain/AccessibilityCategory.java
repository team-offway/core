package com.offway.core.trip.domain;

/**
 * 무장애(배리어프리) 편의 분류 — 어떤 이용약자에게 도움이 되는 편의인지로 묶는다. TourAPI 무장애정보(detailWithTour2)의 30여 개
 * 필드를 화면이 다루기 쉬운 네 갈래로 접는다.
 */
public enum AccessibilityCategory {

    /** 이동약자(휠체어·지체) — 주차·경사로·엘리베이터·장애인 화장실 등. */
    MOBILITY("이동약자"),

    /** 시각장애 — 점자블록·음성안내·안내견 동반 등. */
    VISUAL("시각장애"),

    /** 청각장애 — 수어·영상 안내 등. */
    HEARING("청각장애"),

    /** 영유아·가족 — 유모차 대여·수유실·유아용 의자 등. */
    INFANT("영유아·가족");

    private final String label;

    AccessibilityCategory(String label) {
        this.label = label;
    }

    /** 화면 표시용 한글 분류명. */
    public String label() {
        return label;
    }
}
