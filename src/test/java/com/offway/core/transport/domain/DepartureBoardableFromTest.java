package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 언제부터 탈 수 있나(#422).
 *
 * <p><b>오늘과 그 이후를 다르게 다뤄야 한다.</b> 계획 시각만 보면 오늘 코스에서 이미 지난 차가 목록
 * 맨 위에 뜨고, 지금 시각만 보면 밤에 짠 다음 달 코스가 하루치를 통째로 잃는다.
 */
class DepartureBoardableFromTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);
    private static final LocalTime MORNING = LocalTime.of(9, 0);

    @Test
    void 오늘이고_계획_시각이_지났으면_지금부터다() {
        // 실제로 겪은 값 — 20:37 에 오늘 코스를 여니 첫 편이 08:57 이었다.
        LocalDateTime now = TODAY.atTime(20, 37);

        assertEquals(LocalTime.of(20, 37), Departure.boardableFrom(TODAY, MORNING, now));
    }

    @Test
    void 오늘이지만_아직_계획_시각_전이면_계획대로다() {
        // 계획보다 이르게 나설 수는 없다. 07시에 열어도 09시 이후 편만 보여준다.
        LocalDateTime now = TODAY.atTime(7, 0);

        assertEquals(MORNING, Departure.boardableFrom(TODAY, MORNING, now));
    }

    /**
     * <b>내일 이후는 지금 시각과 무관하다.</b>
     *
     * <p>밤 11시에 다음 달 코스를 짜는 것이 이 서비스의 기본 사용법이다 — 지금 시각을 바닥으로 쓰면
     * 그 코스의 시간표가 통째로 빈다.
     */
    @Test
    void 내일_이후는_계획_시각을_그대로_쓴다() {
        LocalDateTime lateTonight = TODAY.atTime(23, 30);

        assertEquals(MORNING, Departure.boardableFrom(TODAY.plusDays(1), MORNING, lateTonight));
        assertEquals(MORNING, Departure.boardableFrom(TODAY.plusMonths(1), MORNING, lateTonight));
    }

    /** 지난 날짜도 같은 규칙이다 — 오늘이 아니면 계획 시각을 쓴다. */
    @Test
    void 지난_날짜도_계획_시각을_그대로_쓴다() {
        assertEquals(MORNING, Departure.boardableFrom(TODAY.minusDays(1), MORNING, TODAY.atTime(20, 37)));
    }

    @Test
    void 계획과_지금이_같으면_그_시각이다() {
        assertEquals(MORNING, Departure.boardableFrom(TODAY, MORNING, TODAY.atTime(MORNING)));
    }

    @Test
    void 인자가_비면_거절한다() {
        LocalDateTime now = TODAY.atStartOfDay();

        assertThrows(NullPointerException.class, () -> Departure.boardableFrom(null, MORNING, now));
        assertThrows(NullPointerException.class, () -> Departure.boardableFrom(TODAY, null, now));
        assertThrows(NullPointerException.class, () -> Departure.boardableFrom(TODAY, MORNING, null));
    }
}
