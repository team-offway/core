package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.itinerary.controller.dto.CourseResponse;
import com.offway.core.leave.controller.dto.AvailableTimeResponse;
import com.offway.core.trip.controller.dto.PoiDetailResponse;
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

    /** {@code regionId=16} 만으로는 로그를 훑을 때 어디 코스인지 알 수 없다 — 사람이 아는 이름이 필요하다. */
    @Test
    void 코스는_지역명과_규모를_낸다() {
        CourseResponse.Item item = new CourseResponse.Item(
                1, "MORNING", "SIGHT", "관광", "c1", "장소1", null, null, null, null, null, null, 37.5, 128.6, 0, null, "정선군");
        CourseResponse.Day day = new CourseResponse.Day(1, null, null, null, null, null, List.of(item));
        CourseResponse response = new CourseResponse(
                1L, 16, 3, null, "PACKED", "CAR", List.of(day), List.of(), null, null, null);

        assertEquals("정선군 코스 3일 1슬롯", response.logSummary());
    }

    /** 지역명을 못 채운 코스도 로그를 남겨야 한다 — 요약이 통째로 빠지면 그 요청만 흔적이 없다. */
    @Test
    void 지역명이_없으면_지역미상으로_낸다() {
        CourseResponse response =
                new CourseResponse(1L, 16, 1, null, "PACKED", "CAR", List.of(), List.of(), null, null, null);

        assertEquals("지역미상 코스 1일 0슬롯", response.logSummary());
    }

    @Test
    void 장소_상세는_이름과_분류를_낸다() {
        PoiDetailResponse response = new PoiDetailResponse(
                "126508", 12, "관광지", "완도타워 전망대", null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertEquals("완도타워 전망대(관광지)", response.logSummary());
    }

    @Test
    void 가용시간은_계산_결과만_낸다() {
        AvailableTimeResponse response =
                new AvailableTimeResponse(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 21), 3, 3.0, 420);

        // 날짜는 요청 쿼리에 이미 드러나므로 로그에는 싣지 않는다.
        assertEquals("3일 연차3.0 도달420분", response.logSummary());
    }
}
