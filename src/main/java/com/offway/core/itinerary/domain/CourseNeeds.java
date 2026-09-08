package com.offway.core.itinerary.domain;

/**
 * 코스에 필요한 장소 수(course-logic ④). 밀도와 일수에서 도출한다 — 볼거리 = 일수 × 밀도, 맛집 = 일수 × 2(점심·저녁), 숙박 = 박수.
 *
 * @param sights 필요 볼거리 수
 * @param foods 필요 맛집 수
 * @param stays 필요 숙박 수(박수)
 */
public record CourseNeeds(int sights, int foods, int cafes, int stays) {

    /** 하루 식사(점심·저녁) 수. */
    private static final int MEALS_PER_DAY = 2;

    /**
     * 하루 카페 수 — <b>점심 뒤 한 곳</b>(#522).
     *
     * <p>밥 다음은 보통 카페다. 두 곳을 넣으면 하루가 카페로 채워지고, 안 넣으면 예전처럼 카페가
     * 끼니 자리를 차지하거나 코스에서 통째로 사라진다.
     */
    private static final int CAFES_PER_DAY = 1;

    public static CourseNeeds of(Density density, int travelDays) {
        if (density == null) {
            throw new IllegalArgumentException("일정 밀도는 필수입니다");
        }
        if (travelDays < 1 || travelDays > Course.MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException("여행 일수는 1~" + Course.MAX_TRAVEL_DAYS + "일이어야 합니다: " + travelDays);
        }
        return new CourseNeeds(
                density.sightsPerDay() * travelDays,
                MEALS_PER_DAY * travelDays,
                CAFES_PER_DAY * travelDays,
                travelDays - 1);
    }
}
