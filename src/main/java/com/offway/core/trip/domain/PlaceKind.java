package com.offway.core.trip.domain;

import com.offway.core.itinerary.domain.SlotKind;

/**
 * 장소의 종류(#144) — 목록 화면의 상단 탭이자 코스 풀의 구분.
 *
 * <p>카페를 맛집에서 갈라 둔다. 코스의 <b>식사 슬롯</b>에는 카페가 들어가면 안 되지만, 목록에서는 사용자가
 * 따로 보고 싶어 하는 갈래다. 한 종류로 묶으면 둘 중 하나를 포기하게 된다.
 *
 * <p>itinerary 의 {@code SlotKind} 와 이름이 겹치지만 별개다. 이쪽은 trip 이 소유한 후보 분류이고, 저쪽은
 * 코스에 배치된 슬롯의 분류다. 도메인 경계를 넘어 enum 을 공유하면 한쪽 변경이 다른 쪽을 끌고 간다.
 */
public enum PlaceKind {
    SIGHT("관광명소"),
    FOOD("맛집"),
    CAFE("카페"),
    STAY("숙소");

    private final String label;

    PlaceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 코스에서 이 종류가 앉는 슬롯(#172).
     *
     * <p>혜택이 슬롯 종류로 매칭되므로(숙박세일페스타 → 숙소) 장소 상세에서도 같은 기준으로 물어야 한다.
     * 카페는 식사 슬롯에 안 들어가지만 혜택 관점에서는 음식점과 같은 자리다.
     */
    public SlotKind slotKind() {
        return switch (this) {
            case STAY -> SlotKind.STAY;
            case FOOD, CAFE -> SlotKind.FOOD;
            case SIGHT -> SlotKind.SIGHT;
        };
    }
}
