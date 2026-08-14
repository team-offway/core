package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void 코스_차감은_영_일도_기록한다() {
        // 주말·공휴일뿐인 구간이면 깎을 연차가 없다. 그래도 확정은 확정이다 — 이 행이 차감량이자
        // 확정 표식이라, 0 을 막으면 주말 여행을 확정할 수 없게 된다(#212).
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 0, "코스 확정", 7L, false);

        assertEquals(0, usage.getDays());
    }

    @Test
    void 코스_차감을_영_일로_옮길_수_있다() {
        // 날짜를 주말로 옮겼다고 확정이 풀리면 안 된다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, false);

        usage.moveTo(LocalDate.of(2026, 10, 5), 0);

        assertEquals(0, usage.getDays());
        assertEquals(LocalDate.of(2026, 10, 5), usage.getUsedOn());
    }

    @Test
    void 수동_내역은_영_일을_받지_않는다() {
        // 코스 차감과 규칙이 다르다. 수동 내역은 순수한 증감 장부라 0 이 그대로 소음이다.
        assertThrows(LeaveException.class, () -> LeaveUsage.manual("guest-1", WHEN, 0, "개인 사유"));
    }

    @Test
    void 코스_차감은_음수를_받지_않는다() {
        // 코스 차감의 취소는 음수 누적이 아니라 행 삭제다(#113).
        assertThrows(LeaveException.class, () -> LeaveUsage.forCourse("guest-1", WHEN, -1.0, "코스 확정", 7L, false));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, -2, -99})
    void 수동_내역은_아직_음수를_받는다(double days) {
        // 되돌리기는 삭제가 맞지만, 거절을 지금 켜면 앱이 갈아타기 전 구간에서 취소가 끊긴다 —
        // #276 으로 미뤘다. 미뤄도 잔여가 총을 넘는 증상은 LeaveSummary 의 clamp 가 막는다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, days, "취소");

        assertEquals(days, usage.getDays());
        assertTrue(usage.isManual(), "상쇄 등록도 수동 내역이라 사용자가 삭제로 정리할 수 있다");
    }

    @Test
    void 수동_내역은_손으로_지울_수_있다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유");

        assertTrue(usage.isManual());
        assertDoesNotThrow(usage::requireManuallyDeletable);
    }

    @Test
    void 코스_확정_내역은_연차_화면에서_지울_수_없다() {
        // 그 행은 차감량이자 확정 표식이다 — 지우면 코스는 확정인데 연차는 안 깎인 상태가 남는다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, false);

        LeaveException thrown = assertThrows(LeaveException.class, usage::requireManuallyDeletable);

        assertEquals(LeaveErrorCode.COURSE_LEAVE_USAGE_NOT_DELETABLE, thrown.errorCode());
        assertFalse(usage.isManual());
    }
}
