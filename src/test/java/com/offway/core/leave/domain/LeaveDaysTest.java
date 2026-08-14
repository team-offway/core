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
    @ValueSource(doubles = {0, 0.5, 1, 1.5, 15, 99})
    void 총_연차는_0에서_99_사이의_0점5_단위다(double days) {
        assertTrue(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, 0.3, 1.1, 99.5, 365, 1000})
    void 총_연차가_음수거나_상한_밖이거나_0점5_단위가_아니면_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1, 3, 99, -0.5, -3, -99})
    void 사용_증감은_음수도_허용하고_상한은_총_연차와_같다(double days) {
        // 음수(상쇄 등록)를 아직 받는다 — 거절은 앱이 삭제 API 로 갈아탄 뒤로 미뤘다(#276).
        // 먼저 켜면 앱의 취소가 400 을 받아 그 구간 동안 사용자가 취소를 못 한다.
        // ±99 를 함께 둔 이유: 사용 증감도 MAX_TOTAL 을 쓰는데, 경계가 없으면 상한이 예전 365 로
        // 남아 있어도 이 테스트가 통과한다(#142 가 99 로 좁힌 계약을 못 지킨다).
        assertTrue(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.3, -0.2, 99.5, -99.5, 400})
    void 사용_증감이_0이거나_0점5_단위가_아니거나_상한_밖이면_거부한다(double days) {
        // 0 은 아무것도 바꾸지 않는 기록이라 소음이다.
        // ±99.5 는 상한 바로 바깥 — 이게 없으면 상한이 365 여도 400 만 걸려 통과한다.
        assertFalse(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않은_값은_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
        assertFalse(LeaveDays.isValidUsage(days));
    }
}
