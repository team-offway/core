package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.trip.controller.dto.RegionRecommendResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseLogSummaryTest {

    @Test
    void 여행지_추천은_건수와_상위_다섯건을_낸다() {
        List<RegionRecommendResponse.Item> items = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new RegionRecommendResponse.Item(
                        i, "지역" + i + " · 도", 100 + i, null, null, 10, List.of(), false, List.of()))
                .toList();

        RegionRecommendResponse response = new RegionRecommendResponse(items);

        assertEquals(
                "추천=20건 (1:지역1 2:지역2 3:지역3 4:지역4 5:지역5 …외 15건)",
                response.logSummary());
    }

    @Test
    void 여행지_추천이_비면_건수0이다() {
        assertEquals("추천=0건 ()", new RegionRecommendResponse(List.of()).logSummary());
    }

    @Test
    void 가용시간은_확정_기간과_계산_결과를_낸다() {
        AvailableTimeResponse response = new AvailableTimeResponse(
                LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21), 3, 3.0, 420);

        assertEquals("2026-08-19~2026-08-21 travelDays=3 소모연차=3.0 도달한계=420분", response.logSummary());
    }
}
