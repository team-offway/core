package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"NA", "HS", "VE", "LS", "EV", "AC", "EX", "FD", "ZZ"})
    void ALL은_어떤_코드든_포함한다(String code) {
        assertTrue(Category.ALL.includes(code));
    }

    @Test
    void SIGHT는_자연역사문화유산레포츠행사_묶음만_포함한다() {
        assertTrue(Category.SIGHT.includes("NA"));
        assertTrue(Category.SIGHT.includes("HS"));
        assertTrue(Category.SIGHT.includes("VE"));
        assertTrue(Category.SIGHT.includes("LS"));
        assertTrue(Category.SIGHT.includes("EV"));
        assertFalse(Category.SIGHT.includes("AC"));
        assertFalse(Category.SIGHT.includes("EX"));
        assertFalse(Category.SIGHT.includes("FD"));
    }

    @Test
    void STAY_EXPERIENCE_FOOD는_각자_한_코드만_포함한다() {
        assertTrue(Category.STAY.includes("AC"));
        assertFalse(Category.STAY.includes("NA"));
        assertTrue(Category.EXPERIENCE.includes("EX"));
        assertFalse(Category.EXPERIENCE.includes("NA"));
        assertTrue(Category.FOOD.includes("FD"));
        assertFalse(Category.FOOD.includes("NA"));
    }

    @Test
    void lclsSystm1Codes는_필터에_실을_코드묶음을_주고_ALL은_빈집합이다() {
        assertEquals(Set.of("NA", "HS", "VE", "LS", "EV"), Category.SIGHT.lclsSystm1Codes());
        assertEquals(Set.of("AC"), Category.STAY.lclsSystm1Codes());
        assertTrue(Category.ALL.lclsSystm1Codes().isEmpty());
    }
}
