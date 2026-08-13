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
    @ValueSource(doubles = {0.5, 1, 3, 99})
    void 사용_일수는_0점5_단위_양수다(double days) {
        // 99 를 함께 둔 이유: 사용 일수도 MAX_TOTAL 을 쓰는데, 경계가 없으면 상한이 예전 365 로
        // 남아 있어도 이 테스트가 통과한다(#142 가 99 로 좁힌 계약을 못 지킨다).
        assertTrue(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.3, -0.2, 99.5, -99.5, 400, -0.5, -1, -99})
    void 사용_일수가_0이거나_음수거나_0점5_단위가_아니거나_상한_밖이면_거부한다(double days) {
        // 0 은 아무것도 바꾸지 않는 기록이라 소음이다.
        // 음수는 예전엔 취소를 뜻해 받았는데, 같은 취소가 두 번 들어오면 잔여가 총을 넘었다(#265).
        // ±99.5 는 상한 바로 바깥 — 이게 없으면 상한이 365 여도 400 만 걸려 통과한다.
        assertFalse(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, -2, -99, -0.3})
    void 음수는_상쇄_등록으로_가른다(double days) {
        // 사유가 갈려야 화면이 "삭제로 취소하세요" 를 안내한다 — 0.5 단위 위반과 같은 코드로 답하면
        // 사용자는 자기가 숫자를 잘못 넣은 줄 안다.
        assertTrue(LeaveDays.isReversal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.5, 1, 99, 0.3})
    void 영_이상은_상쇄_등록이_아니다(double days) {
        assertFalse(LeaveDays.isReversal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않은_값은_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
        assertFalse(LeaveDays.isValidUsage(days));
    }
}
