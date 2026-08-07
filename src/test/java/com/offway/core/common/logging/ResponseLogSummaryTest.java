package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.trip.controller.dto.RegionRecommendResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResponseLogSummaryTest {

    @Test
    void 여행지_추천은_건수만_낸다() {
        List<RegionRecommendResponse.Item> items = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new RegionRecommendResponse.Item(
                        i, "지역" + i + " · 도", 100 + i, null, null, 10, List.of(), false, List.of()))
                .toList();

        assertEquals("추천 20건", new RegionRecommendResponse(items).logSummary());
    }

    @Test
    void 여행지_추천이_비면_0건이다() {
        assertEquals("추천 0건", new RegionRecommendResponse(List.of()).logSummary());
    }

    @Test
    void 가용시간은_계산_결과만_낸다() {
        AvailableTimeResponse response =
                new AvailableTimeResponse(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21), 3, 3.0, 420);

        // 날짜는 요청 쿼리에 이미 드러나므로 로그에는 싣지 않는다.
        assertEquals("3일 연차3.0 도달420분", response.logSummary());
    }
}
