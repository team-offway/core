package com.offway.core.trip.domain;

/**
 * 인허가 장소가 코스의 어느 풀에 담기는지(#144) — 볼거리·맛집·숙박.
 *
 * <p>itinerary 의 {@code SlotKind} 와 이름이 같지만 별개다. 이쪽은 trip 이 소유한 후보 분류이고, 저쪽은 코스에 배치된
 * 슬롯의 분류다. 도메인 경계를 넘어 enum 을 공유하면 한쪽 변경이 다른 쪽을 끌고 간다.
 */
public enum PlaceKind {
    SIGHT("볼거리"),
    FOOD("맛집"),
    STAY("숙박");

    private final String label;

    PlaceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
