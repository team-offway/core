package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 남은 연차 파생 단위 테스트. */
class LeaveSummaryTest {

    @Test
    void 남은_연차는_총_연차에서_사용_합을_뺀_값이다() {
        assertEquals(13.0, new LeaveSummary(15.0, 2.0).remainingDays());
    }

    @Test
    void 취소가_섞이면_사용_합이_줄어_남은_연차가_늘어난다() {
        // 3일 쓰고 1일 취소(-1) → 사용 합 2
        assertEquals(13.0, new LeaveSummary(15.0, 3.0 - 1.0).remainingDays());
    }

    @Test
    void 초과_사용하면_남은_연차가_음수다() {
        // 서버가 초과를 막지 않으므로(결정 #38) 0 으로 깎지 않는다 — 얼마나 초과했는지를 화면이 알아야 한다.
        assertEquals(-2.0, new LeaveSummary(3.0, 5.0).remainingDays());
    }

    @Test
    void 반차가_섞여도_0점5_단위로_떨어진다() {
        assertEquals(12.5, new LeaveSummary(15.0, 2.5).remainingDays());
    }
}
