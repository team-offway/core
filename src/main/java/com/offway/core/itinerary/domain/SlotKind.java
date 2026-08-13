package com.offway.core.itinerary.domain;

import com.offway.core.policy.domain.BenefitScope;

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

    /**
     * 그 혜택이 붙는 슬롯 — 정책이 말하는 대상을 코스의 언어로 옮긴다(#140).
     *
     * <p><b>대응을 코스 쪽이 소유한다.</b> "숙박세일페스타는 숙소에서 쓴다" 는 프로그램의 성질이라 정책이
     * 알지만, 그게 코스의 어느 자리인지는 코스가 안다. 정책이 {@link SlotKind} 를 들면
     * {@code policy → itinerary} 의존이 생기는데, 코스는 이미 정책을 참조하므로 두 도메인이 서로를
     * 가리키게 된다.
     *
     * <p>{@code switch} 가 모든 상수를 덮으므로 {@link BenefitScope} 에 새 대상이 생기면 <b>여기서
     * 컴파일이 깨진다</b> — 코스가 자리를 정하지 않은 채로 넘어가지 않는다.
     */
    public static SlotKind covering(BenefitScope scope) {
        return switch (scope) {
            case LODGING -> STAY;
        };
    }
}
