package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 연차 사용 내역 한 건. 코스 날짜가 바뀌면 이 행이 따라 옮겨진다(#170).
 */
class LeaveUsageTest {

    private static final LocalDate WHEN = LocalDate.of(2026, 9, 11);

    @Test
    void 코스_내역은_반차_여부를_기억한다() {
        // 이 값이 없으면 날짜를 옮길 때 재계산이 종일로 되돌아가 0.5 를 더 깎는다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 1.5, "코스 확정", 7L, true);

        assertTrue(usage.isHalfDayStart());
    }

    @Test
    void 반차_여부는_차감_일수에서_되짚을_수_없다() {
        // 출발일이 주말·공휴일이면 반차를 골라도 차감이 정수로 나온다. 소수점 유무로 판단하면 여기서 틀린다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, true);

        assertTrue(usage.isHalfDayStart(), "정수 차감이어도 반차였다");
    }

    @Test
    void 수동_내역은_반차_개념이_없다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유");

        assertFalse(usage.isHalfDayStart());
    }

    @Test
    void 코스_내역을_새_날짜와_일수로_옮긴다() {
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, false);

        usage.moveTo(LocalDate.of(2026, 10, 5), 1.0);

        assertEquals(LocalDate.of(2026, 10, 5), usage.getUsedOn());
        assertEquals(1.0, usage.getDays());
    }

    @Test
    void 옮겨도_반차_여부는_그대로다() {
        // 사용자가 확정할 때 고른 값이다. 날짜를 고쳤다고 바뀌지 않는다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 1.5, "코스 확정", 7L, true);

        usage.moveTo(LocalDate.of(2026, 10, 5), 2.5);

        assertTrue(usage.isHalfDayStart());
    }

    @Test
    void 수동_내역은_코스_날짜_변경으로_옮길_수_없다() {
        // 코스가 없는 내역이라 여기 닿으면 호출한 쪽이 잘못 짚은 것이다 — 계약이 아니라 불변식이다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유");

        assertThrows(IllegalStateException.class, () -> usage.moveTo(LocalDate.of(2026, 10, 5), 1.0));
    }

    @Test
    void 반차_단위가_아닌_일수로는_옮길_수_없다() {
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, false);

        assertThrows(LeaveException.class, () -> usage.moveTo(LocalDate.of(2026, 10, 5), 1.3));
        assertEquals(2.0, usage.getDays(), "거절했으면 원래 값이 남아야 한다");
    }

    @Test
    void 영_일로는_옮길_수_없다() {
        // 아무것도 바꾸지 않는 내역은 기록이 아니라 소음이다(LeaveDays). 깎을 평일이 없어지면 행을 지운다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, false);

        assertThrows(LeaveException.class, () -> usage.moveTo(LocalDate.of(2026, 10, 5), 0));
    }
}
