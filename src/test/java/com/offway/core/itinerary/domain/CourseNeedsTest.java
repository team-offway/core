package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseNeedsTest {

    @Test
    void 빡빡_2일이면_볼거리12_맛집4_숙박1() {
        CourseNeeds needs = CourseNeeds.of(Density.PACKED, 2);

        assertEquals(12, needs.sights()); // 6/일 × 2
        assertEquals(4, needs.foods()); // 2/일 × 2
        assertEquals(1, needs.stays()); // 박수 = 2-1
    }

    @Test
    void 널널_3일이면_볼거리9_맛집6_숙박2() {
        CourseNeeds needs = CourseNeeds.of(Density.RELAXED, 3);

        assertEquals(9, needs.sights()); // 3/일 × 3
        assertEquals(6, needs.foods());
        assertEquals(2, needs.stays());
    }

    @Test
    void 당일치기는_숙박이_0이다() {
        assertEquals(0, CourseNeeds.of(Density.PACKED, 1).stays());
    }

    @Test
    void 밀도가_없거나_일수가_1미만_또는_2박3일_초과면_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> CourseNeeds.of(null, 2));
        assertThrows(IllegalArgumentException.class, () -> CourseNeeds.of(Density.PACKED, 0));
        assertThrows(IllegalArgumentException.class, () -> CourseNeeds.of(Density.PACKED, 4)); // 최대 2박3일
    }
}
