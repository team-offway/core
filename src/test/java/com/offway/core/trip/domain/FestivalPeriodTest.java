package com.offway.core.trip.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 축제 기간의 불변식(#388).
 *
 * <p>어댑터도 같은 값을 거르지만 엔티티는 <b>누가 만들든</b> 스스로 유효함을 보장하는 최후의 보루다.
 * 여기가 뚫리면 어떤 날짜에도 안 열리는 축제가 저장되어, 있는 축제를 우리가 지우게 된다.
 */
class FestivalPeriodTest {

    private static final LocalDateTime FETCHED_AT = LocalDateTime.of(2026, 9, 1, 4, 20);

    private static FestivalPeriod.FestivalPeriodBuilder valid() {
        return FestivalPeriod.builder()
                .contentId("2733967")
                .eventStart(LocalDate.of(2026, 9, 12))
                .eventEnd(LocalDate.of(2026, 9, 14))
                .title("장보고수산물축제")
                .fetchedAt(FETCHED_AT);
    }

    @Test
    void 시작이_종료보다_늦으면_거절한다() {
        FestivalPeriod.FestivalPeriodBuilder reversed =
                valid().eventStart(LocalDate.of(2026, 9, 14)).eventEnd(LocalDate.of(2026, 9, 12));

        assertThrows(IllegalArgumentException.class, reversed::build);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void contentId_가_비면_거절한다(String contentId) {
        // 붙일 곳을 모르는 기간은 쓸모가 없다.
        assertThrows(RuntimeException.class, () -> valid().contentId(contentId).build());
    }

    @Test
    void 날짜가_비면_거절한다() {
        assertThrows(NullPointerException.class, () -> valid().eventStart(null).build());
        assertThrows(NullPointerException.class, () -> valid().eventEnd(null).build());
    }

    @ParameterizedTest
    @CsvSource({
        // 시작일·종료일 당일을 포함한다 — 첫날·마지막날에 가는 사람이 가장 많다
        "2026-09-12, true",
        "2026-09-13, true",
        "2026-09-14, true",
        "2026-09-11, false",
        "2026-09-15, false",
    })
    void 여행일에_열리는지_판정한다(LocalDate travelDate, boolean expected) {
        assertEquals(expected, valid().build().isOpenOn(travelDate));
    }

    @Test
    void 하루짜리_축제도_그날은_열린다() {
        LocalDate oneDay = LocalDate.of(2026, 9, 12);

        assertTrue(valid().eventStart(oneDay).eventEnd(oneDay).build().isOpenOn(oneDay));
    }

    @Test
    void contentId_의_공백은_다듬는다() {
        // 외부 응답에 공백이 섞여 오면 조회 키가 어긋나 기간을 못 찾는다 — 값이 있는데 없는 것처럼 된다.
        assertEquals("2733967", valid().contentId("  2733967  ").build().getContentId());
    }
}
