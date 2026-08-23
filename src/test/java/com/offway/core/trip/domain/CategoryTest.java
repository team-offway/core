package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @ParameterizedTest
    @ValueSource(strings = {"NA", "HS", "VE", "LS", "EV", "AC", "EX", "FD", "ZZ"})
    void ALL은_어떤_코드든_포함한다(String code) {
        assertTrue(Category.ALL.includes(code));
    }

    @Test
    void SIGHT는_자연역사탈것행사쇼핑_묶음만_포함한다() {
        assertTrue(Category.SIGHT.includes("NA"));
        assertTrue(Category.SIGHT.includes("HS"));
        assertTrue(Category.SIGHT.includes("VE"));
        assertTrue(Category.SIGHT.includes("EV"));
        // 쇼핑을 여기 넣었다(#304). 칩은 넷으로 고정이라 자리를 못 주는데, 빼면 저장조차 안 돼
        // 전체 탭에서도 사라진다 — 실측에서 전 지역 33건이 그렇게 버려지고 있었다.
        assertTrue(Category.SIGHT.includes("SH"));
        // 레포츠는 체험으로 옮겼다(#304). 관광지에 두면 체험 칩이 사실상 비었다.
        assertFalse(Category.SIGHT.includes("LS"));
        assertFalse(Category.SIGHT.includes("AC"));
        assertFalse(Category.SIGHT.includes("EX"));
        assertFalse(Category.SIGHT.includes("FD"));
    }

    /**
     * 체험은 <b>둘</b>을 든다 — 체험(EX)과 레포츠(LS).
     *
     * <p>실측(2026-08-21, 89곳 전수)에서 순수 EX 만으로는 체험 칩이 얇았다. 레포츠를 옮기니 전 지역
     * 합계가 412 → 581건(+41%)이 됐고, 정선군은 8 → 17건이 되어 레일바이크·짚와이어·스키장이
     * 비로소 체험으로 잡힌다.
     */
    @Test
    void EXPERIENCE는_체험과_레포츠를_함께_든다() {
        assertTrue(Category.EXPERIENCE.includes("EX"));
        assertTrue(Category.EXPERIENCE.includes("LS"));
        assertFalse(Category.EXPERIENCE.includes("NA"));
        assertFalse(Category.EXPERIENCE.includes("AC"));
    }

    @Test
    void STAY_와_FOOD는_각자_한_코드만_포함한다() {
        assertTrue(Category.STAY.includes("AC"));
        assertFalse(Category.STAY.includes("NA"));
        assertTrue(Category.FOOD.includes("FD"));
        assertFalse(Category.FOOD.includes("NA"));
    }

    @Test
    void lclsSystm1Codes는_필터에_실을_코드묶음을_주고_ALL은_빈집합이다() {
        assertEquals(Set.of("NA", "HS", "VE", "EV", "SH"), Category.SIGHT.lclsSystm1Codes());
        assertEquals(Set.of("EX", "LS"), Category.EXPERIENCE.lclsSystm1Codes());
        assertEquals(Set.of("AC"), Category.STAY.lclsSystm1Codes());
        assertTrue(Category.ALL.lclsSystm1Codes().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"NA,SIGHT", "HS,SIGHT", "EV,SIGHT", "SH,SIGHT", "AC,STAY", "EX,EXPERIENCE", "LS,EXPERIENCE", "FD,FOOD"})
    void fromLclsSystm1은_코드를_소유한_구체칩으로_되돌린다(String code, Category expected) {
        assertEquals(Optional.of(expected), Category.fromLclsSystm1(code));
    }

    @Test
    void fromLclsSystm1은_미지의코드나_null이면_빈Optional이고_ALL로는_매핑하지_않는다() {
        assertEquals(Optional.empty(), Category.fromLclsSystm1("ZZ"));
        assertEquals(Optional.empty(), Category.fromLclsSystm1(null));
    }
}
