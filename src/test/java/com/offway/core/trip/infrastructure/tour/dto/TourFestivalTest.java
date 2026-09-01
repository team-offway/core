package com.offway.core.trip.infrastructure.tour.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 축제 응답 한 줄을 기간으로 옮기는 규칙(#388).
 *
 * <p><b>여기가 헐거우면 있는 축제를 우리가 지운다.</b> 날짜가 깨진 줄을 억지로 채우면 그 축제가 어떤
 * 날짜에도 안 열리는 것이 되고, 코스에서 조용히 사라진다.
 */
class TourFestivalTest {

    @Test
    void 온전한_줄은_기간이_된다() {
        Optional<TourFestival> festival = TourFestival.of("2733967", "장보고수산물축제", "20260912", "20260914");

        assertTrue(festival.isPresent());
        assertEquals(LocalDate.of(2026, 9, 12), festival.get().eventStart());
        assertEquals(LocalDate.of(2026, 9, 14), festival.get().eventEnd());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void contentId_가_없으면_버린다(String contentId) {
        // 붙일 곳을 모르는 기간은 쓸모가 없다.
        assertTrue(TourFestival.of(contentId, "축제", "20260912", "20260914").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                // 한쪽만 있으면 "지금 열리나" 를 판정할 수 없다
                "NULL, 20260914",
                "20260912, NULL",
                "'', 20260914",
                // 형식이 깨진 값 — 한 줄 때문에 페이지를 날리지 않는다
                "2026-09-12, 20260914",
                "abcd, 20260914",
                // 거꾸로 온 날짜 — 그대로 두면 어떤 날짜에도 안 열린다
                "20260914, 20260912",
            })
    void 날짜가_온전하지_않으면_버린다(String start, String end) {
        assertTrue(TourFestival.of("2733967", "축제", start, end).isEmpty());
    }

    @Test
    void 하루짜리_축제도_정상이다() {
        Optional<TourFestival> oneDay = TourFestival.of("1", "하루축제", "20260912", "20260912");

        assertTrue(oneDay.isPresent());
        assertTrue(oneDay.get().isOpenOn(LocalDate.of(2026, 9, 12)));
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
        TourFestival festival =
                TourFestival.of("1", "축제", "20260912", "20260914").orElseThrow();

        assertEquals(expected, festival.isOpenOn(travelDate));
    }

    @Test
    void 제목이_없어도_기간은_살린다() {
        // 제목은 로그용이라 없어도 판정에 지장이 없다. 여기서 버리면 멀쩡한 기간을 잃는다.
        Optional<TourFestival> festival = TourFestival.of("1", "  ", "20260912", "20260914");

        assertTrue(festival.isPresent());
        assertFalse(festival.get().isOpenOn(LocalDate.of(2026, 9, 15)));
    }
}
