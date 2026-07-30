package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 확정 구간 값객체 단위 테스트.
 *
 * <p>여기서 검증하는 것은 <b>불변식</b>이다 — 요청 DTO 경계가 이미 계약(400)으로 걸렀고 스타일 해석은 애초에 유효한 구간만
 * 만들므로, 이 예외가 실제로 터지면 그건 버그 신호(500)다. 그래도 마지막 보루가 작동하는지는 확인해둔다.
 */
class TripPeriodTest {

    @ParameterizedTest
    @CsvSource({
        "2026-05-06, 2026-05-06, 1",
        "2026-05-06, 2026-05-07, 2",
        "2026-05-06, 2026-05-08, 3",
    })
    void 시작일부터_종료일까지_하루씩_센다(LocalDate start, LocalDate end, int expectedDays) {
        assertEquals(expectedDays, new TripPeriod(start, end).days());
    }

    @Test
    void 종료일이_시작일보다_앞서면_불변식_위반이다() {
        LocalDate start = LocalDate.of(2026, 5, 8);
        LocalDate end = LocalDate.of(2026, 5, 6);

        assertThrows(IllegalArgumentException.class, () -> new TripPeriod(start, end));
    }

    @Test
    void 여행_상한을_넘으면_불변식_위반이다() {
        LocalDate start = LocalDate.of(2026, 5, 4);
        LocalDate end = start.plusDays(AvailableTime.MAX_TRIP_DAYS); // 상한 + 1일

        assertThrows(IllegalArgumentException.class, () -> new TripPeriod(start, end));
    }

    @Test
    void 극단적으로_먼_종료일도_상한_검사에_걸린다() {
        // int 로 먼저 캐스팅하면 랩어라운드로 상한 검사를 우연히 통과할 수 있다 — long 으로 재는지 확인.
        LocalDate start = LocalDate.of(2026, 5, 4);
        LocalDate end = LocalDate.of(999999999, 12, 31);

        assertThrows(IllegalArgumentException.class, () -> new TripPeriod(start, end));
    }

    @Test
    void 날짜가_null_이면_불변식_위반이다() {
        LocalDate date = LocalDate.of(2026, 5, 6);

        assertThrows(NullPointerException.class, () -> new TripPeriod(null, date));
        assertThrows(NullPointerException.class, () -> new TripPeriod(date, null));
    }
}
