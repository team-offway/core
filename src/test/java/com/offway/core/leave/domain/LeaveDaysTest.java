package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 연차 값 규칙 단위 테스트.
 *
 * <p>0.5 단위를 지키는 게 핵심이다 — 반차가 0.5 라(결정 #38) 스테퍼에 1.5 를 직접 넣는다. 0.3 같은 값이 통과하면
 * 이후 합·차감이 조용히 이상해진다.
 */
class LeaveDaysTest {

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.5, 1, 1.5, 15, 365})
    void 총_연차는_0에서_365_사이의_0점5_단위다(double days) {
        assertTrue(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, 0.3, 1.1, 365.5, 1000})
    void 총_연차가_음수거나_상한_밖이거나_0점5_단위가_아니면_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1, 3, -0.5, -3})
    void 사용_증감은_음수도_허용한다(double days) {
        // 코스를 취소하면 음수 내역으로 되돌린다 — 행을 지우면 취소 이력이 사라진다.
        assertTrue(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.3, -0.2, 400})
    void 사용_증감이_0이거나_0점5_단위가_아니면_거부한다(double days) {
        // 0 은 아무것도 바꾸지 않는 기록이라 소음이다.
        assertFalse(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않은_값은_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
        assertFalse(LeaveDays.isValidUsage(days));
    }
}
