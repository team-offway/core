package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 1.5, "코스 확정", 7L, StartDayLeave.HALF_DAY);

        assertEquals(StartDayLeave.HALF_DAY, usage.startDayLeave());
    }

    @Test
    void 반차_여부는_차감_일수에서_되짚을_수_없다() {
        // 출발일이 주말·공휴일이면 반차를 골라도 차감이 정수로 나온다. 소수점 유무로 판단하면 여기서 틀린다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.HALF_DAY);

        assertEquals(StartDayLeave.HALF_DAY, usage.startDayLeave(), "정수 차감이어도 반차였다");
    }

    @Test
    void 수동_내역은_반차_개념이_없다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        assertEquals(StartDayLeave.FULL_DAY, usage.startDayLeave(), "수동 내역은 첫날 단위 개념이 없어 종일이다");
    }

    @Test
    void 코스_내역을_새_날짜와_일수로_옮긴다() {
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        usage.moveTo(LocalDate.of(2026, 10, 5), 1.0);

        assertEquals(LocalDate.of(2026, 10, 5), usage.getUsedOn());
        assertEquals(1.0, usage.getDays());
    }

    @Test
    void 옮겨도_반차_여부는_그대로다() {
        // 사용자가 확정할 때 고른 값이다. 날짜를 고쳤다고 바뀌지 않는다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 1.5, "코스 확정", 7L, StartDayLeave.HALF_DAY);

        usage.moveTo(LocalDate.of(2026, 10, 5), 2.5);

        assertEquals(StartDayLeave.HALF_DAY, usage.startDayLeave());
    }

    @Test
    void 수동_내역은_코스_날짜_변경으로_옮길_수_없다() {
        // 코스가 없는 내역이라 여기 닿으면 호출한 쪽이 잘못 짚은 것이다 — 계약이 아니라 불변식이다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        assertThrows(IllegalStateException.class, () -> usage.moveTo(LocalDate.of(2026, 10, 5), 1.0));
    }

    @Test
    void 반차_단위가_아닌_일수로는_옮길_수_없다() {
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        assertThrows(LeaveException.class, () -> usage.moveTo(LocalDate.of(2026, 10, 5), 1.3));
        assertEquals(2.0, usage.getDays(), "거절했으면 원래 값이 남아야 한다");
    }

    @Test
    void 코스_차감은_영_일도_기록한다() {
        // 주말·공휴일뿐인 구간이면 깎을 연차가 없다. 그래도 확정은 확정이다 — 이 행이 차감량이자
        // 확정 표식이라, 0 을 막으면 주말 여행을 확정할 수 없게 된다(#212).
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        assertEquals(0, usage.getDays());
    }

    @Test
    void 코스_차감을_영_일로_옮길_수_있다() {
        // 날짜를 주말로 옮겼다고 확정이 풀리면 안 된다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        usage.moveTo(LocalDate.of(2026, 10, 5), 0);

        assertEquals(0, usage.getDays());
        assertEquals(LocalDate.of(2026, 10, 5), usage.getUsedOn());
    }

    @Test
    void 수동_내역은_영_일을_받지_않는다() {
        // 코스 차감과 규칙이 다르다. 수동 내역은 순수한 증감 장부라 0 이 그대로 소음이다.
        assertThrows(LeaveException.class, () -> LeaveUsage.manual("guest-1", WHEN, 0, "개인 사유", null));
    }

    @Test
    void 코스_차감은_음수를_받지_않는다() {
        // 코스 차감의 취소는 음수 누적이 아니라 행 삭제다(#113).
        assertThrows(LeaveException.class, () -> LeaveUsage.forCourse("guest-1", WHEN, -1.0, "코스 확정", 7L, StartDayLeave.FULL_DAY));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, -1, -2, -99})
    void 수동_내역도_음수를_받지_않는다(double days) {
        // 상쇄 등록이 잔여를 총 연차보다 크게 만들던 자리다(#265). 되돌리기는 이제 삭제다.
        LeaveException thrown =
                assertThrows(LeaveException.class, () -> LeaveUsage.manual("guest-1", WHEN, days, "취소", null));

        assertEquals(LeaveErrorCode.LEAVE_USAGE_REVERSAL_NOT_ALLOWED, thrown.errorCode(),
                "단위 위반과 사유가 달라야 화면이 '삭제로 취소하세요' 를 안내한다");
    }

    @Test
    void 수동_내역은_손으로_고치고_지울_수_있다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        assertTrue(usage.isManual());
        assertDoesNotThrow(usage::requireManuallyManaged);
    }

    // ── 내역 수정(#267) ────────────────────────────────────────

    @Test
    void 보낸_필드만_바뀌고_나머지는_그대로다() {
        // PATCH 의 의미다. 안 보낸 필드를 기본값으로 덮으면 사용자가 건드리지도 않은 값이 사라진다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        usage.edit(null, 2.5, null, null);

        assertEquals(2.5, usage.getDays());
        assertEquals(WHEN, usage.getUsedOn());
        assertEquals("개인 사유", usage.getReason());
    }

    @Test
    void 빈_사유를_보내면_사유가_지워진다() {
        // 빠진 필드와 명시적 null 이 똑같이 null 로 도착해 구분되지 않는다. 빈 문자열이 "지워라" 를
        // 표현하는 유일한 신호다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        usage.edit(null, null, "", null);

        assertNull(usage.getReason());
    }

    @Test
    void 아무것도_안_보내면_아무것도_바뀌지_않는다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        usage.edit(null, null, null, null);

        assertEquals(WHEN, usage.getUsedOn());
        assertEquals(1.0, usage.getDays());
        assertEquals("개인 사유", usage.getReason());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.3, 100})
    void 수정도_등록과_같은_값_규칙을_탄다(double days) {
        // 같은 값에 계약이 두 개면 화면이 어느 쪽을 따를지 알 수 없다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        LeaveException thrown = assertThrows(LeaveException.class, () -> usage.edit(null, days, null, null));

        assertEquals(LeaveErrorCode.INVALID_LEAVE_USAGE_DAYS, thrown.errorCode());
    }

    @Test
    void 수정으로_음수를_넣는_것도_상쇄_등록으로_가른다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        LeaveException thrown = assertThrows(LeaveException.class, () -> usage.edit(null, -1.0, null, null));

        assertEquals(LeaveErrorCode.LEAVE_USAGE_REVERSAL_NOT_ALLOWED, thrown.errorCode());
    }

    @Test
    void 거절된_수정은_아무것도_바꾸지_않는다() {
        // 검증을 대입보다 먼저 끝내는 이유다. 날짜만 바뀐 반쪽 상태가 남으면 사용자는 실패했다고
        // 들었는데 화면의 날짜는 바뀌어 있다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "개인 사유", null);

        assertThrows(LeaveException.class, () -> usage.edit(WHEN.plusDays(3), 0.3, "바뀐 사유", null));

        assertEquals(WHEN, usage.getUsedOn());
        assertEquals(1.0, usage.getDays());
        assertEquals("개인 사유", usage.getReason());
    }

    // ── 상세 메모(#319) ────────────────────────────────────────

    @Test
    void 사유와_메모는_서로_다른_칸이다() {
        // 한 칸에 몰면 둘 중 하나는 담을 자리가 없다 — 화면에 입력이 따로 있는 것이 이 필드의 이유다.
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "제주 여행", "숙소 체크인 15시");

        assertEquals("제주 여행", usage.getReason());
        assertEquals("숙소 체크인 15시", usage.getMemo());
    }

    @Test
    void 메모만_고쳐도_사유는_그대로다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "제주 여행", "옛 메모");

        usage.edit(null, null, null, "새 메모");

        assertEquals("새 메모", usage.getMemo());
        assertEquals("제주 여행", usage.getReason());
    }

    @Test
    void 빈_메모를_보내면_메모만_지워진다() {
        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, "제주 여행", "지울 메모");

        usage.edit(null, null, null, "");

        assertNull(usage.getMemo());
        assertEquals("제주 여행", usage.getReason(), "메모를 지운다고 사유까지 사라지면 안 된다");
    }

    @Test
    void 메모가_상한을_넘으면_400_이_아니라_자른다() {
        // 부가 정보라 요청을 되돌릴 만큼은 아니다. 사유와 같은 규칙이고, 길이만 다르다.
        String tooLong = "가".repeat(LeaveUsage.MAX_MEMO_LENGTH + 50);

        LeaveUsage usage = LeaveUsage.manual("guest-1", WHEN, 1.0, null, tooLong);

        assertEquals(LeaveUsage.MAX_MEMO_LENGTH, usage.getMemo().length());
    }

    @Test
    void 코스_확정_내역에는_메모가_없다() {
        // 사용자가 쓰는 칸인데 이 행은 서버가 만든다 — 채울 사람이 없다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        assertNull(usage.getMemo());
    }

    @Test
    void 코스_확정_내역은_연차_화면에서_지울_수_없다() {
        // 그 행은 차감량이자 확정 표식이다 — 지우면 코스는 확정인데 연차는 안 깎인 상태가 남는다.
        LeaveUsage usage = LeaveUsage.forCourse("guest-1", WHEN, 2.0, "코스 확정", 7L, StartDayLeave.FULL_DAY);

        LeaveException thrown = assertThrows(LeaveException.class, usage::requireManuallyManaged);

        assertEquals(LeaveErrorCode.COURSE_LEAVE_USAGE_NOT_DELETABLE, thrown.errorCode());
        assertFalse(usage.isManual());
    }
}
