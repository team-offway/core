package com.offway.core.liveactivity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.liveactivity.domain.TripProgress.Phase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 잠금화면에 보낼 숫자를 정하는 계산(#575).
 *
 * <p><b>이 계산이 틀리면 기능이 통째로 무의미해진다.</b> 잠금화면 갱신의 존재 이유가 "하루가 지나면
 * 숫자가 바뀐다" 하나라, 여기서 하루가 어긋나면 고치려던 증상이 그대로 남는다. 분기 폭이 큰 영역이라
 * 단위로 망라한다.
 *
 * <p>기준 여행: 2026-09-23 출발, 2박 3일(종료 2026-09-25).
 */
class TripProgressTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 23);

    private static final int THREE_DAYS = 3;

    @ParameterizedTest(name = "{0} 이면 출발 {1}일 전")
    @CsvSource({
        "2026-09-18, 5",
        "2026-09-21, 2",
        "2026-09-22, 1",
    })
    void 출발_전이면_남은_날을_센다(LocalDate today, int expected) {
        TripProgress progress = TripProgress.of(START, THREE_DAYS, today);

        assertEquals(Phase.BEFORE, progress.phase());
        assertEquals(expected, progress.daysLeft());
        assertNull(progress.dayNth(), "출발 전인데 며칠째가 들어 있다 — 앱이 어느 쪽을 그릴지 정할 수 없다");
    }

    @ParameterizedTest(name = "{0} 이면 {1}일차")
    @CsvSource({
        "2026-09-23, 1",
        "2026-09-24, 2",
        "2026-09-25, 3",
    })
    void 여행_중이면_며칠째인지_센다(LocalDate today, int expected) {
        TripProgress progress = TripProgress.of(START, THREE_DAYS, today);

        assertEquals(Phase.DURING, progress.phase());
        assertEquals(expected, progress.dayNth());
        assertNull(progress.daysLeft(), "여행 중인데 남은 날이 들어 있다 — 앱이 어느 쪽을 그릴지 정할 수 없다");
    }

    /**
     * <b>출발 당일은 {@code D-0} 이 아니라 1일차다.</b>
     *
     * <p>이미 떠나온 사람에게 "0일 남았다" 는 알려주는 것이 없다. 경계를 {@code isBefore} 가 아니라
     * {@code !isAfter} 로 잡으면 여기가 {@code BEFORE} 로 떨어지는데, 그러면 화면에 {@code D-0} 이 뜬다.
     */
    @Test
    void 출발_당일은_남은_날이_0이_아니라_1일차다() {
        TripProgress progress = TripProgress.of(START, THREE_DAYS, START);

        assertEquals(Phase.DURING, progress.phase());
        assertEquals(1, progress.dayNth());
        assertNull(progress.daysLeft());
    }

    /** 며칠째는 <b>1부터</b> 센다 — 0일차는 없다. */
    @Test
    void 며칠째는_0이_아니라_1부터_센다() {
        assertEquals(1, TripProgress.of(START, THREE_DAYS, START).dayNth());
    }

    @Test
    void 종료일_다음_날부터_끝난_것이다() {
        assertEquals(Phase.DURING, TripProgress.of(START, THREE_DAYS, LocalDate.of(2026, 9, 25)).phase());
        assertEquals(Phase.ENDED, TripProgress.of(START, THREE_DAYS, LocalDate.of(2026, 9, 26)).phase());
    }

    @Test
    void 끝났으면_숫자를_담지_않는다() {
        TripProgress progress = TripProgress.of(START, THREE_DAYS, LocalDate.of(2026, 10, 1));

        assertTrue(progress.ended());
        assertNull(progress.daysLeft());
        assertNull(progress.dayNth());
    }

    /**
     * 날짜 없는 코스는 <b>끝난 것으로 본다</b>.
     *
     * <p>언제인지 모르는 여행을 잠금화면에 띄워 둘 수 없다. 여기서 예외를 던지면 그 카드 하나 때문에
     * 그날 갱신 전체가 흔들린다.
     */
    @Test
    void 날짜가_없으면_끝난_것으로_본다() {
        assertTrue(TripProgress.of(null, 1, LocalDate.of(2026, 9, 23)).ended());
    }

    @Test
    void 당일치기는_그날_하루만_여행_중이다() {
        assertEquals(Phase.BEFORE, TripProgress.of(START, 1, START.minusDays(1)).phase());
        assertEquals(1, TripProgress.of(START, 1, START).dayNth());
        assertEquals(Phase.ENDED, TripProgress.of(START, 1, START.plusDays(1)).phase());
    }

    /**
     * 달로 세지 않는다 — <b>달력 날짜</b>로 센다.
     *
     * <p>월 길이가 다른 경계에서 "며칠 남았나" 를 달 단위로 환산하면 어긋난다.
     */
    @Test
    void 월을_넘겨도_날짜로_센다() {
        LocalDate start = LocalDate.of(2026, 12, 31);

        assertEquals(2, TripProgress.of(start, THREE_DAYS, LocalDate.of(2026, 12, 29)).daysLeft());
        // 2026-12-31 출발 2박3일 → 종료는 2027-01-02 다. 해를 넘겨도 3일차다.
        assertEquals(3, TripProgress.of(start, THREE_DAYS, LocalDate.of(2027, 1, 2)).dayNth());
        assertEquals(Phase.ENDED, TripProgress.of(start, THREE_DAYS, LocalDate.of(2027, 1, 3)).phase());
    }

    /** 윤년의 2월 29일을 하루로 센다 — 있는 날을 없는 셈 치면 3월 여행이 하루씩 밀린다. */
    @Test
    void 윤년의_2월_29일도_하루다() {
        LocalDate start = LocalDate.of(2028, 2, 28);

        assertEquals(2, TripProgress.of(start, THREE_DAYS, LocalDate.of(2028, 2, 29)).dayNth());
        assertEquals(3, TripProgress.of(start, THREE_DAYS, LocalDate.of(2028, 3, 1)).dayNth());
    }

    @Test
    void 종료일은_출발일에서_기간만큼이다() {
        assertEquals(LocalDate.of(2026, 9, 23), TripProgress.endDate(START, 1));
        assertEquals(LocalDate.of(2026, 9, 25), TripProgress.endDate(START, THREE_DAYS));
    }
}
