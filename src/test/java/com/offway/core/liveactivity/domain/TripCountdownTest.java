package com.offway.core.liveactivity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.liveactivity.domain.TripCountdown.Trip;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 오늘 잠금화면에 올릴 여행 <b>하나</b>를 고르는 규칙(#583).
 *
 * <p><b>앱에 같은 규칙이 있다.</b> 어긋나면 앱을 연 사람과 안 연 사람이 서로 다른 카드를 보는데,
 * 그건 둘 중 하나가 틀렸다는 뜻이고 사용자는 어느 쪽이 맞는지 알 수 없다. 그래서 분기를 여기서 망라한다.
 */
class TripCountdownTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Test
    void 아무것도_없으면_안_띄운다() {
        assertTrue(TripCountdown.pick(List.of(), TODAY).isEmpty());
    }

    @Test
    void 출발이_5일_이내면_띄운다() {
        Trip trip = new Trip(1L, TODAY.plusDays(5), 2);

        assertEquals(1L, TripCountdown.pick(List.of(trip), TODAY).orElseThrow().courseId());
    }

    /** 창이 {@value TripCountdown#WITHIN_DAYS}일이다 — 6일 남은 여행은 아직 아니다. */
    @Test
    void 출발이_창_밖이면_안_띄운다() {
        Trip trip = new Trip(1L, TODAY.plusDays(6), 2);

        assertTrue(TripCountdown.pick(List.of(trip), TODAY).isEmpty());
    }

    @Test
    void 여행_중이면_띄운다() {
        Trip trip = new Trip(1L, TODAY.minusDays(1), 3);

        assertEquals(1L, TripCountdown.pick(List.of(trip), TODAY).orElseThrow().courseId());
    }

    @Test
    void 마지막_날도_여행_중이다() {
        Trip trip = new Trip(1L, TODAY.minusDays(2), 3);

        assertEquals(1L, TripCountdown.pick(List.of(trip), TODAY).orElseThrow().courseId());
    }

    @Test
    void 끝난_여행은_안_띄운다() {
        Trip trip = new Trip(1L, TODAY.minusDays(3), 3);

        assertTrue(TripCountdown.pick(List.of(trip), TODAY).isEmpty());
    }

    /**
     * <b>진행 중이 앞으로 떠날 것을 이긴다.</b>
     *
     * <p>지금 그 지역에 있는 사람에게 다음 주 여행을 보여줄 이유가 없다. 순서를 뒤집으면 여행 중에
     * 잠금화면이 엉뚱한 여행의 D-day 를 띄운다.
     */
    @Test
    void 진행_중이_있으면_그것을_띄운다() {
        Trip ongoing = new Trip(1L, TODAY.minusDays(1), 3);
        Trip soon = new Trip(2L, TODAY.plusDays(1), 2);

        assertEquals(1L, TripCountdown.pick(List.of(soon, ongoing), TODAY).orElseThrow().courseId());
    }

    @Test
    void 진행_중이_둘이면_출발이_이른_것을_띄운다() {
        Trip earlier = new Trip(1L, TODAY.minusDays(2), 4);
        Trip later = new Trip(2L, TODAY.minusDays(1), 3);

        assertEquals(1L, TripCountdown.pick(List.of(later, earlier), TODAY).orElseThrow().courseId());
    }

    @Test
    void 앞으로_떠날_것이_둘이면_가장_가까운_것을_띄운다() {
        Trip nearer = new Trip(1L, TODAY.plusDays(2), 2);
        Trip farther = new Trip(2L, TODAY.plusDays(4), 2);

        assertEquals(1L, TripCountdown.pick(List.of(farther, nearer), TODAY).orElseThrow().courseId());
    }

    /** 오늘 떠나는 여행은 <b>진행 중</b>이다 — "0일 남았다" 가 아니다. */
    @Test
    void 오늘_출발은_진행_중으로_본다() {
        Trip today = new Trip(1L, TODAY, 2);
        Trip tomorrow = new Trip(2L, TODAY.plusDays(1), 2);

        assertEquals(1L, TripCountdown.pick(List.of(tomorrow, today), TODAY).orElseThrow().courseId());
    }

    @Test
    void 날짜가_없는_코스는_고르지_않는다() {
        Trip undated = new Trip(1L, null, 2);
        Trip dated = new Trip(2L, TODAY.plusDays(1), 2);

        assertEquals(2L, TripCountdown.pick(List.of(undated, dated), TODAY).orElseThrow().courseId());
    }

    /**
     * 기간이 0 이하면 종료일이 출발일보다 앞서는 <b>역전</b>이다.
     *
     * <p>그대로 두면 "0일차" 나 음수 D-day 가 잠금화면에 뜬다. 그리고 이 카드는 <b>앱을 안 연 사람</b>
     * 에게 가므로, 이상한 값을 보고도 우리에게 알릴 길이 없다.
     */
    @Test
    void 기간이_0_이하인_코스는_고르지_않는다() {
        Trip broken = new Trip(1L, TODAY, 0);

        assertTrue(TripCountdown.pick(List.of(broken), TODAY).isEmpty());
    }

    @Test
    void 깨진_코스가_섞여_있어도_멀쩡한_것은_띄운다() {
        Trip broken = new Trip(1L, TODAY.minusDays(1), 0);
        Trip fine = new Trip(2L, TODAY.plusDays(2), 2);

        assertEquals(2L, TripCountdown.pick(List.of(broken, fine), TODAY).orElseThrow().courseId());
    }

    @Test
    void 종료일은_출발일에서_기간만큼이다() {
        assertEquals(LocalDate.of(2026, 9, 22), new Trip(1L, TODAY, 3).endDate());
        assertEquals(TODAY, new Trip(1L, TODAY, 1).endDate());
    }
}
