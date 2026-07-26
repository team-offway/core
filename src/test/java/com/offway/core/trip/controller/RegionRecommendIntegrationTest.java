package com.offway.core.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.StubTourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
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
class RegionRecommendIntegrationTest {

    private static final String URL = "/api/v1/regions/recommendations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourDataLabClient dataLabClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourDataLabClient stubTourDataLabClient() {
            return new StubTourDataLabClient();
        }
    }

    @Test
    void 도달_가능한_지역을_랭킹순으로_혜택뱃지와_함께_추천한다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty); // 방문자 데이터 없음 → 89 모두 동점(LOW)

        // 서울 출발 + 도달 한계 아주 크게 → 89 전부 도달 가능
        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regions.length()").value(89))
                .andExpect(jsonPath("$.data.regions[0].regionId").exists())
                .andExpect(jsonPath("$.data.regions[0].name").exists())
                .andExpect(jsonPath("$.data.regions[0].reachMinutes").exists())
                .andExpect(jsonPath("$.data.regions[0].crowdLevel").value("LOW"))
                // 전 지역이 인구감소지역 → 반값여행(운영기간 내) 뱃지가 붙는다
                .andExpect(jsonPath("$.data.regions[0].benefits[0].text").value("여행경비 50% 환급"));
    }

    @Test
    void 도달_한계가_아주_작으면_추천이_비어있다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 1 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(0));
    }

    @Test
    void 좌표가_범위를_벗어나면_400() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);

        String body = """
                { "originLat": 200.0, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 420 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 관광빅데이터_조회_실패는_502_TOUR_002() throws Exception {
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("TOUR-002"));
    }
}
