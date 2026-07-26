package com.offway.core.itinerary.domain;

/**
 * 코스에 필요한 장소 수(course-logic ④). 밀도와 일수에서 도출한다 — 볼거리 = 일수 × 밀도, 맛집 = 일수 × 2(점심·저녁), 숙박 = 박수.
 *
 * @param sights 필요 볼거리 수
 * @param foods 필요 맛집 수
 * @param stays 필요 숙박 수(박수)
 */
public record CourseNeeds(int sights, int foods, int stays) {

    /** 하루 식사(점심·저녁) 수. */
    private static final int MEALS_PER_DAY = 2;

    public static CourseNeeds of(Density density, int travelDays) {
        if (density == null) {
            throw new IllegalArgumentException("일정 밀도는 필수입니다");
        }
        if (travelDays < 1) {
            throw new IllegalArgumentException("여행 일수는 1 이상이어야 합니다: " + travelDays);
        }
        return new CourseNeeds(
                density.sightsPerDay() * travelDays,
                MEALS_PER_DAY * travelDays,
                travelDays - 1);
    }
}
