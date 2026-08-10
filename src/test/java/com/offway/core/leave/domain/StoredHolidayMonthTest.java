package com.offway.core.leave.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StoredHolidayMonthTest {

    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 5, 1, 9, 0);

    @Test
    void 저장했다_읽으면_같은_날짜_집합이다() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 25));

        StoredHolidayMonth stored = StoredHolidayMonth.of(MAY, dates, UPDATED);

        assertEquals(dates, stored.holidays());
        assertEquals(MAY, stored.month());
    }

    @Test
    void 공휴일이_없는_달도_저장된다_빈_집합으로_읽힌다() {
        // 이 케이스가 이 클래스의 존재 이유다 — "공휴일 없는 달"과 "안 받아온 달"이 구분돼야 한다.
        StoredHolidayMonth stored = StoredHolidayMonth.of(YearMonth.of(2026, 4), Set.of(), UPDATED);

        assertEquals(Set.of(), stored.holidays());
        assertEquals("202604", stored.getBaseYm());
    }

    @Test
    void 기준_연월_밖의_날짜가_섞이면_거부한다() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 5, 5), LocalDate.of(2026, 6, 6));

        assertThrows(IllegalArgumentException.class, () -> StoredHolidayMonth.of(MAY, dates, UPDATED));
    }

    @Test
    void 달을_꽉_채워도_저장_한도_안에_들어간다() {
        // 저장 한도(400자)는 컬럼 길이와 짝이다. 한 달은 최대 31일이라 10자 × 31 + 구분자 30 = 340자로
        // 어떤 달도 넘지 못한다 — 상한이 넉넉한지를 계산이 아니라 실제 값으로 못 박는다.
        Set<LocalDate> allDays = IntStream.rangeClosed(1, 31)
                .mapToObj(day -> LocalDate.of(2026, 5, day))
                .collect(Collectors.toUnmodifiableSet());

        StoredHolidayMonth stored = StoredHolidayMonth.of(MAY, allDays, UPDATED);

        assertEquals(31, stored.holidays().size());
    }

    @Test
    void 저장_순서와_무관하게_날짜_오름차순으로_직렬화된다() {
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 5));

        StoredHolidayMonth stored = StoredHolidayMonth.of(MAY, dates, UPDATED);

        assertEquals("2026-05-05,2026-05-25", stored.getHolidays());
    }

    @Test
    void 갱신일_판정은_시각이_아니라_날짜로_한다() {
        StoredHolidayMonth stored =
                StoredHolidayMonth.of(MAY, Set.of(), LocalDateTime.of(2026, 5, 1, 23, 59));

        assertTrue(stored.refreshedOn(LocalDate.of(2026, 5, 1)));
        assertFalse(stored.refreshedOn(LocalDate.of(2026, 5, 2)));
    }
}
