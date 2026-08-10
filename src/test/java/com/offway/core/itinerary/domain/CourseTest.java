package com.offway.core.itinerary.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.TransportMode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static Slot slot(int order) {
        return Slot.of(order, TimeOfDay.MORNING, SlotKind.SIGHT, "c" + order, "장소" + order, 37.5, 127.0,
                order == 1 ? 0 : 10);
    }

    private static DaySchedule day(int dayNumber, int slots) {
        return DaySchedule.of(dayNumber, java.util.stream.IntStream.rangeClosed(1, slots)
                .mapToObj(CourseTest::slot).toList());
    }

    @Test
    void 유효한_코스는_기간을_받아_들고_전체슬롯을_센다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2)), null, 2);

        assertEquals(42L, course.getRegionId());
        assertEquals(2, course.getTravelDays());
        assertEquals(Density.PACKED, course.getDensity());
        assertEquals(5, course.totalSlots()); // 3 + 2
    }

    @Test
    void 하루도_없으면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of(), null, 1));
    }

    @Test
    void 최대_2박3일을_초과하면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR,
                        List.of(day(1, 1), day(2, 1), day(3, 1), day(4, 1)), null, 3));
    }

    @Test
    void 일차가_1부터_연속이_아니면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.RELAXED, TransportMode.CAR, List.of(day(1, 1), day(3, 1)), null, 2));
    }

    @Test
    void 일정이_없는_날이_있어도_기간은_요청한_일수다() {
        // 첫날이 이동뿐이면 그 날은 코스에서 빠진다(#159). 그래도 여행은 3일짜리다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.TRANSIT,
                List.of(DaySchedule.of(1, 1, List.of(slot(1))), DaySchedule.of(2, 2, List.of(slot(1)))),
                LocalDate.of(2026, 9, 11), 3);

        assertEquals(3, course.getTravelDays(), "표시 일수(2)가 아니라 달력 기간(3)");
        assertEquals(LocalDate.of(2026, 9, 13), course.travelEndDate(),
                "종료일이 하루 이르면 연차가 덜 차감되고 \"다녀오셨나요\" 가 일찍 뜬다(#164)");
        assertFalse(course.hasEndedBy(LocalDate.of(2026, 9, 13)), "종료 당일은 아직 여행 중이다");
        assertTrue(course.hasEndedBy(LocalDate.of(2026, 9, 14)));
    }

    @Test
    void 기간_밖에_일정이_있으면_거부한다() {
        // 기간을 잘못 넘기면 여기서 걸려야 한다 — 조용히 통과하면 연차 차감이 그만큼 어긋난다.
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR,
                        List.of(DaySchedule.of(1, 0, List.of(slot(1))), DaySchedule.of(2, 2, List.of(slot(1)))),
                        LocalDate.of(2026, 9, 11), 2));
    }

    @Test
    void 기간이_범위를_벗어나면_거부한다() {
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 1)), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 1)), null, 4));
    }

    @Test
    void 일차는_느는데_날짜가_거꾸로_가면_거부한다() {
        // 기간 검사는 최대 오프셋만 보므로 이 조합을 통과시킨다 — 그러면 화면 순서와 날짜 순서가 역전된다.
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR,
                        List.of(DaySchedule.of(1, 1, List.of(slot(1))), DaySchedule.of(2, 0, List.of(slot(1)))),
                        LocalDate.of(2026, 9, 11), 3));
    }

    @Test
    void 두_일차가_같은_날짜를_가리키면_거부한다() {
        // 하루를 두 번 쓰는 셈이라 그 날짜에 무엇이 있는지 답할 수 없다.
        assertThrows(IllegalArgumentException.class,
                () -> Course.of(42L, Density.PACKED, TransportMode.CAR,
                        List.of(DaySchedule.of(1, 1, List.of(slot(1))), DaySchedule.of(2, 1, List.of(slot(1)))),
                        LocalDate.of(2026, 9, 11), 3));
    }

    @Test
    void 중간_날이_통째로_비어_오프셋이_건너뛰어도_받는다() {
        // 엄격 증가면 충분하다 — 1씩 늘 것을 요구하면 중간이 빈 코스(#159)를 거부하게 된다.
        Course course = Course.of(42L, Density.RELAXED, TransportMode.CAR,
                List.of(DaySchedule.of(1, 0, List.of(slot(1))), DaySchedule.of(2, 2, List.of(slot(1)))),
                LocalDate.of(2026, 9, 11), 3);

        assertEquals(LocalDate.of(2026, 9, 13), course.dateOf(course.getDays().get(1)));
    }

    /**
     * 실시간 값(대기질)을 붙일지 가르는 판정 — 예보가 아니라 지금 이 순간의 측정치라, 다음 주 코스에 붙으면
     * 사용자가 여행일 상태로 읽는다.
     */
    @Test
    void 오늘_출발이면_여행이_오늘을_포함한다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2)),
                LocalDate.of(2026, 9, 11), 2);

        assertTrue(course.covers(LocalDate.of(2026, 9, 11)));
    }

    @Test
    void 여행_중이면_시작일이_지났어도_오늘을_포함한다() {
        // 시작일만 보면 이튿날에 값이 사라진다 — 정작 그 지역에 가 있는 날이다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2), day(3, 2)),
                LocalDate.of(2026, 9, 11), 3);

        assertTrue(course.covers(LocalDate.of(2026, 9, 12)));
        assertTrue(course.covers(LocalDate.of(2026, 9, 13)), "종료 당일도 여행 중이다");
    }

    @Test
    void 여행_전후는_오늘을_포함하지_않는다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2), day(3, 2)),
                LocalDate.of(2026, 9, 11), 3);

        assertFalse(course.covers(LocalDate.of(2026, 9, 10)), "하루 전");
        assertFalse(course.covers(LocalDate.of(2026, 9, 14)), "하루 뒤");
    }

    @Test
    void 날짜_없는_코스는_오늘을_포함하지_않는다() {
        // 언제인지 모르는 것을 "오늘 여행 중" 으로 답하면 안 된다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3)), null, 1);

        assertFalse(course.covers(LocalDate.of(2026, 9, 11)));
    }

    @Test
    void 여행_날짜를_옮기면_종료일도_함께_옮겨진다() {
        // 기간은 그대로다 — 사용자가 고친 것은 "언제 떠나는가" 지 "며칠짜리인가" 가 아니다.
        Course course = threeDayCourse(LocalDate.of(2026, 9, 11));

        course.changeTravelDate(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 1));

        assertEquals(LocalDate.of(2026, 10, 5), course.getTravelDate());
        assertEquals(LocalDate.of(2026, 10, 7), course.travelEndDate());
        assertEquals(3, course.getTravelDays());
    }

    @Test
    void 옮긴_날짜로_일차별_날짜가_다시_계산된다() {
        Course course = threeDayCourse(LocalDate.of(2026, 9, 11));

        course.changeTravelDate(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 1));

        assertEquals(LocalDate.of(2026, 10, 5), course.dateOf(course.getDays().get(0)));
        assertEquals(LocalDate.of(2026, 10, 6), course.dateOf(course.getDays().get(1)));
    }

    @Test
    void 지난_날짜로는_옮길_수_없다() {
        // 옮기는 순간 "끝난 여행" 이 돼 홈이 "다녀오셨나요?" 를 묻는데, 사용자는 계획을 고쳤을 뿐이다(#170).
        Course course = threeDayCourse(LocalDate.of(2026, 9, 11));

        assertThrows(ItineraryException.class,
                () -> course.changeTravelDate(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)));
        assertEquals(LocalDate.of(2026, 9, 11), course.getTravelDate(), "거절했으면 원래 날짜가 남아야 한다");
    }

    @Test
    void 오늘로는_옮길_수_있다() {
        // 경계 — 오늘은 아직 지나지 않았다.
        Course course = threeDayCourse(LocalDate.of(2026, 9, 11));

        course.changeTravelDate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

        assertEquals(LocalDate.of(2026, 9, 1), course.getTravelDate());
    }

    @Test
    void 지금_날짜가_이미_지났어도_앞으로는_옮길_수_있다() {
        // 판단 대상은 옮겨갈 날짜다. 날짜를 놓친 코스를 당겨오는 것이 이 기능이 가장 필요한 경우다.
        Course course = threeDayCourse(LocalDate.of(2026, 8, 1));

        course.changeTravelDate(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 1));

        assertEquals(LocalDate.of(2026, 9, 20), course.getTravelDate());
    }

    @Test
    void 날짜_없이_저장된_코스도_날짜를_넣어_고칠_수_있다() {
        // 이 컬럼이 생기기 전에 저장된 코스다. 날짜를 넣어야 연차를 차감할 수 있다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3)), null, 1);

        course.changeTravelDate(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 1));

        assertEquals(LocalDate.of(2026, 9, 20), course.getTravelDate());
    }

    private static Course threeDayCourse(LocalDate travelDate) {
        return Course.of(42L, Density.PACKED, TransportMode.CAR,
                List.of(day(1, 3), day(2, 2), day(3, 2)), travelDate, 3);
    }

    /**
     * 혜택 매칭의 기준일 — 정책에 유효기간이 있어 이 값이 결과를 가른다(#213).
     */
    @Test
    void 여행일이_있으면_그것이_기준일이다() {
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3), day(2, 2)),
                LocalDate.of(2026, 9, 11), 2);

        assertEquals(LocalDate.of(2026, 9, 11), course.travelDateOr(LocalDate.of(2026, 8, 10)));
    }

    @Test
    void 날짜_없이_저장된_코스는_넘겨받은_기준으로_물러선다() {
        // 이 컬럼이 생기기 전에 저장된 코스가 있다. 아무것도 안 보여주는 쪽이 더 틀린다고 보고 오늘로 본다.
        Course course = Course.of(42L, Density.PACKED, TransportMode.CAR, List.of(day(1, 3)), null, 1);

        assertEquals(LocalDate.of(2026, 8, 10), course.travelDateOr(LocalDate.of(2026, 8, 10)));
    }
}
