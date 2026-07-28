package com.offway.core.itinerary.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CourseGenerateIntegrationTest {

    private static final String URL = "/api/v1/courses/generate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private StubKmaWeatherClient weatherClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }

        @Bean
        @Primary
        KmaWeatherClient stubKmaWeatherClient() {
            return new StubKmaWeatherClient();
        }
    }

    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, "NA", "장소" + id, "부산 동구", lat, lng, "http://img/" + id + ".jpg", null);
    }

    private static TourPoiResult richPois() {
        List<TourPoi> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
        }
        items.add(poi("f0", 39, 35.11, 129.04));
        items.add(poi("f1", 39, 35.12, 129.05));
        items.add(poi("st0", 32, 35.10, 129.03));
        return new TourPoiResult(items, items.size());
    }

    @Test
    void 코스를_생성해_날짜별_타임라인과_혜택을_200으로_내린다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.travelDays").value(2))
                .andExpect(jsonPath("$.data.density").value("PACKED"))
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].order").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0))
                .andExpect(jsonPath("$.data.days[0].items[0].kind").exists())
                .andExpect(jsonPath("$.data.days[0].items[0].lat").exists())
                // 인구감소지역(부산 동구) + 시드 정책 기간 내 → 반값여행 혜택
                .andExpect(jsonPath("$.data.benefits[0].text").value("여행경비 50% 환급"));
    }

    @Test
    void 코스에_여행날짜_날씨를_함께_내린다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        LocalDate date = LocalDate.of(2026, 5, 1);
        weatherClient.respond(() -> Optional.of(new DailyWeather(date, 18, 27, SkyState.CLEAR, 20)));

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weather.date").value("2026-05-01"))
                .andExpect(jsonPath("$.data.weather.minTemp").value(18))
                .andExpect(jsonPath("$.data.weather.maxTemp").value(27))
                .andExpect(jsonPath("$.data.weather.sky").value("맑음"))
                .andExpect(jsonPath("$.data.weather.rainProbability").value(20));
    }

    @Test
    void 여행일수가_2박3일을_초과하면_400() throws Exception {
        String body = """
                { "regionId": 1, "travelDays": 4, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 볼거리가_없는_지역이면_404_ITINERARY_001() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-001"));
    }
}
