package com.offway.core.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** 장소 무장애 정보 엔드포인트 contract — 편의 있음 / 등록 없음(빈 배열 200) / TourAPI 실패(502). */
@SpringBootTest
@AutoConfigureMockMvc
class PoiAccessibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    @Test
    void 등록된_무장애_편의를_분류와_함께_200으로_내린다() throws Exception {
        tourApiClient.respondAccessibility(() -> Optional.of(new TourAccessibility(
                "126508",
                null, null, null, null, null, "대여가능", null, null, "장애인 화장실 있음", null, null, null,
                null, null, null, "음성안내 있음", null, null, null, null,
                null, null, null, null,
                null, "수유실 있음", null, null)));

        mockMvc.perform(get("/api/v1/pois/{id}/accessibility", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.contentId").value("126508"))
                .andExpect(jsonPath("$.data.features.length()").value(4))
                .andExpect(jsonPath("$.data.features[?(@.name == '휠체어')].category").value("MOBILITY"))
                .andExpect(jsonPath("$.data.features[?(@.name == '휠체어')].categoryLabel").value("이동약자"))
                .andExpect(jsonPath("$.data.features[?(@.name == '휠체어')].detail").value("대여가능"));
    }

    @Test
    void 등록_무장애_정보가_없으면_빈_배열로_200이다() throws Exception {
        tourApiClient.respondAccessibility(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}/accessibility", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.contentId").value("999"))
                .andExpect(jsonPath("$.data.features.length()").value(0));
    }

    @Test
    void TourAPI_조회_실패면_502_TOUR_004() throws Exception {
        tourApiClient.respondAccessibility(() -> {
            throw TourApiException.serviceUnavailable();
        });

        mockMvc.perform(get("/api/v1/pois/{id}/accessibility", "126508"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("TOUR-004"));
    }
}
