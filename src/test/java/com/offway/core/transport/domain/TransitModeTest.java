package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 수단별 배차 조회창(#107 · #97).
 *
 * <p>이 값이 실제 조회창보다 짧으면 <b>드문 배차가 미운행으로 굳는다</b>. 여객선을 버스에 맞춰 3일로 자르면
 * 주 몇 편짜리 항로가 없는 길이 되고, 배 말고 닿는 수단이 없는 울릉군은 그대로 "도달 불가" 가 된다.
 * 반대로 길면 어차피 0건인 날을 물어 외부 한도만 태운다.
 */
class TransitModeTest {

    @ParameterizedTest
    @EnumSource(value = TransitMode.class, names = {"EXPRESS_BUS", "INTERCITY_BUS"})
    void 버스는_오늘부터_사흘까지_묻는다(TransitMode mode) {
        assertEquals(3, mode.lookaheadDays());
    }

    @Test
    void 여객선은_버스보다_넓은_여드레를_묻는다() {
        // 버스와 같은 값으로 두면 주 몇 편만 뜨는 항로를 못 보고 미운행으로 적는다.
        assertEquals(8, TransitMode.FERRY.lookaheadDays());
    }

    @Test
    void 열차에는_조회창이_없다() {
        // 열차는 이 표를 쓰지 않고 실제 시각을 직접 답한다. 여기 닿았다면 호출부가 안 걸러낸 것이다.
        assertThrows(IllegalStateException.class, TransitMode.TRAIN::lookaheadDays);
    }
}
