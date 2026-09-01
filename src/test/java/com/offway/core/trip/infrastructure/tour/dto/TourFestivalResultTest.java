package com.offway.core.trip.infrastructure.tour.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 첫 응답이 남은 비용을 확정한다(#388).
 *
 * <p>배치가 <b>끝까지 돌아 보고서</b> 몇 번 불렀는지 아는 것이 아니라, 첫 호출 하나로 남은 호출 수가
 * 정해져야 한다. 이 계산이 틀리면 상한 판정이 어긋나 한도를 예상보다 쓴다.
 */
class TourFestivalResultTest {

    @ParameterizedTest
    @CsvSource({
        "0, 100, 0",
        "1, 100, 1",
        "100, 100, 1",
        // 딱 안 떨어지면 한 페이지 더 — 내림하면 마지막 몇 건이 조용히 빠진다
        "101, 100, 2",
        "250, 100, 3",
        "1000, 100, 10",
    })
    void 전체_건수에서_페이지_수를_낸다(int totalCount, int rows, int expected) {
        assertEquals(expected, new TourFestivalResult(List.of(), totalCount).totalPages(rows));
    }

    @Test
    void 페이지_크기가_0_이하면_거절한다() {
        // 0 으로 나누면 산술 예외가 나는데, 그건 원인을 안 말해 준다.
        assertThrows(IllegalArgumentException.class, () -> new TourFestivalResult(List.of(), 10).totalPages(0));
    }

    @Test
    void 빈_결과는_페이지가_없다() {
        assertEquals(0, TourFestivalResult.empty().totalPages(100));
        assertEquals(0, TourFestivalResult.empty().totalCount());
    }
}
