package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 연차 값 규칙 단위 테스트.
 *
 * <p>0.25 단위를 지키는 게 핵심이다 — 반반차가 0.25 라(#278) 스테퍼에 1.25 를 직접 넣는다. 0.3 같은 값이
 * 통과하면 이후 합·차감이 조용히 이상해진다.
 */
class LeaveDaysTest {

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 15.25, 98.75, 99})
    void 총_연차는_0에서_99_사이의_0점25_단위다(double days) {
        assertTrue(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.25, -0.5, -1, 0.3, 0.1, 1.1, 1.3, 99.25, 365, 1000})
    void 총_연차가_음수거나_상한_밖이거나_0점25_단위가_아니면_거부한다(double days) {
        // 0.1·0.3·1.3 은 0.25 격자 밖이다. 격자를 넓힌다고 아무 소수나 받는 것은 아니다 — 화면 칩이
        // 내주는 값(0.25·0.5·1)과 직접 입력이 같은 규칙을 따라야 잔여 계산이 맞는다.
        assertFalse(LeaveDays.isValidTotal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.5, 0.75, 1, 3.25, 99})
    void 사용_일수는_0점25_단위_양수다(double days) {
        // 99 를 함께 둔 이유: 사용 일수도 MAX_TOTAL 을 쓰는데, 경계가 없으면 상한이 예전 365 로
        // 남아 있어도 이 테스트가 통과한다(#142 가 99 로 좁힌 계약을 못 지킨다).
        assertTrue(LeaveDays.isValidUsage(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.3, -0.2, -0.25, 99.25, -99.25, 400, -0.5, -1, -99})
    void 사용_일수가_0이거나_음수거나_0점25_단위가_아니거나_상한_밖이면_거부한다(double days) {
        // 0 은 아무것도 바꾸지 않는 기록이라 소음이다.
        // 음수는 예전엔 취소를 뜻해 받았는데, 같은 취소가 두 번 들어오면 잔여가 총을 넘었다(#265).
        // ±99.25 는 상한 바로 바깥 — 이게 없으면 상한이 365 여도 400 만 걸려 통과한다.
        assertFalse(LeaveDays.isValidUsage(days));
    }

    @Test
    void 반반차만큼_취소할_수_있다() {
        // 0.25 로 등록한 건은 0.25 로 되돌릴 수 있어야 한다 — 등록만 열고 취소를 막으면 사용자가
        // 자기가 만든 기록을 지우지 못한다. 취소는 음수 등록이 아니라 삭제지만, 사유 판정은 같은 격자를 탄다.
        assertTrue(LeaveDays.isReversal(-0.25));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.25, -0.5, -1, -2, -99, -0.3})
    void 음수는_상쇄_등록으로_가른다(double days) {
        // 사유가 갈려야 화면이 "삭제로 취소하세요" 를 안내한다 — 단위 위반과 같은 코드로 답하면
        // 사용자는 자기가 숫자를 잘못 넣은 줄 안다.
        assertTrue(LeaveDays.isReversal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.25, 0.5, 1, 99, 0.3})
    void 영_이상은_상쇄_등록이_아니다(double days) {
        assertFalse(LeaveDays.isReversal(days));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void 유한하지_않은_값은_거부한다(double days) {
        assertFalse(LeaveDays.isValidTotal(days));
        assertFalse(LeaveDays.isValidUsage(days));
    }

    @Test
    void 첫날_연차_단위는_전부_이_격자_위에_있다() {
        // **이 테스트가 이번 변경의 핵심이다.** 코스 확정 차감은 StartDayLeave 의 소모량을 그대로 쓰는데,
        // 그 값이 이 격자 밖이면 계산이 자기 검증에 걸린다 — 반반차(0.25)가 0.5 격자에 걸려 평일
        // 코스 확정이 400 이던 것이 정확히 그 일이었다. 단위가 늘어도 두 곳이 함께 움직이도록 못박는다.
        for (StartDayLeave startDayLeave : StartDayLeave.values()) {
            assertTrue(
                    LeaveDays.isValidCourseDeduction(startDayLeave.consumedLeave()),
                    startDayLeave + " 의 소모 연차가 연차 격자 밖이다: " + startDayLeave.consumedLeave());
        }
    }
}
