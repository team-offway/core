package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 남은 연차 파생 단위 테스트.
 *
 * <p>핵심은 <b>잔여가 총 연차를 넘지 않는다</b>는 것이다(#265). 위쪽만 막고 아래쪽(초과 사용)은 열어두므로
 * 두 방향의 경계를 함께 본다.
 */
class LeaveSummaryTest {

    @Test
    void 남은_연차는_총_연차에서_사용_합을_뺀_값이다() {
        assertEquals(13.0, LeaveSummary.of(15.0, 2.0).remainingDays());
    }

    @Test
    void 반차가_섞여도_0점5_단위로_떨어진다() {
        assertEquals(12.5, LeaveSummary.of(15.0, 2.5).remainingDays());
    }

    @Test
    void 초과_사용하면_남은_연차가_음수다() {
        // 서버가 초과를 막지 않으므로(결정 #38) 0 으로 깎지 않는다 — 얼마나 초과했는지를 화면이 알아야 한다.
        assertEquals(-2.0, LeaveSummary.of(3.0, 5.0).remainingDays());
    }

    @ParameterizedTest
    @CsvSource({
        "15, 0, 15",     // 아무것도 안 씀 — 잔여 = 총
        "15, 15, 0",     // 딱 맞게 씀 — 잔여 0
        "15, 15.5, -0.5", // 반차만큼 초과
        "0, 0, 0"        // 총 연차 미설정
    })
    void 사용_합이_0_이상이면_그대로_빼서_잔여를_낸다(double total, double ledger, double expected) {
        assertEquals(expected, LeaveSummary.of(total, ledger).remainingDays());
    }

    /**
     * 이 PR 의 존재 이유 — 상쇄 등록이 겹쳐 원장 합이 음수로 내려가도 <b>잔여가 총을 넘지 않는다</b>.
     *
     * <p>총 15일에 2일 쓴 사람이 취소를 두 번 보내면 원장 합이 -2 가 됐고, 예전에는 잔여 17 이 나갔다.
     * 재시도·중복 탭만으로 없던 연차가 생긴 것이다.
     */
    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, -2, -99})
    void 사용_합이_음수여도_잔여가_총_연차를_넘지_않는다(double ledger) {
        LeaveSummary summary = LeaveSummary.of(15.0, ledger);

        assertEquals(15.0, summary.remainingDays(), "잔여는 총 연차가 상한이다");
        assertEquals(0.0, summary.usedDays(), "쓴 연차가 음수라는 말은 뜻이 없다");
    }

    @Test
    void 취소_두_번이_잔여_17을_만들지_않는다() {
        // 프론트가 재현한 시나리오 그대로 — 2일 사용(+2) 뒤 같은 취소가 두 번(-2, -2) 들어온 원장.
        double ledger = 2.0 - 2.0 - 2.0;

        assertEquals(15.0, LeaveSummary.of(15.0, ledger).remainingDays());
    }

    @Test
    void 사용_합이_음수면_잘렸다는_신호를_남긴다() {
        // 조용히 자르고 끝내면 상쇄 등록이 남긴 데이터를 아무도 모른다 — 호출자가 이 값으로 warn 을 남긴다.
        LeaveSummary clamped = LeaveSummary.of(15.0, -2.0);

        assertTrue(clamped.isLedgerNegative());
        assertEquals(-2.0, clamped.ledgerDays(), "원장 합 원본은 그대로 들고 있다");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.5, 2})
    void 사용_합이_0_이상이면_자르지_않는다(double ledger) {
        assertFalse(LeaveSummary.of(15.0, ledger).isLedgerNegative());
    }

    @Test
    void 쓴_연차가_음수인_현황은_만들_수_없다() {
        // 팩토리가 이미 자르므로 여기 닿는 값은 버그다 — 계약 예외가 아니라 불변식이다.
        assertThrows(IllegalArgumentException.class, () -> new LeaveSummary(15.0, -1.0, -1.0));
    }
}
