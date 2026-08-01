package com.offway.core.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class PoiDetailIntegrationTest {

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
    void 장소_상세를_운영시간과_함께_200으로_내린다() throws Exception {
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "완도타워", "전남 완도군", "061-1", 34.3, 126.7, "http://img/1.jpg", "전망대 소개")));
        tourApiClient.respondIntro(() -> Optional.of(new TourIntro("126508", "09:00~18:00", "연중무휴")));

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.title").value("완도타워"))
                .andExpect(jsonPath("$.data.typeLabel").value("관광지")) // contentTypeId 12 → 관광지
                .andExpect(jsonPath("$.data.imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.useTime").value("09:00~18:00"))
                .andExpect(jsonPath("$.data.restDate").value("연중무휴"));
    }

    @Test
    void 캐치프레이즈가_있는_장소면_data에_함께_내린다() throws Exception {
        // 126508 은 시드 CSV(구석구석 캐치프레이즈)에 실제 존재한다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "경복궁", "서울 종로구", null, 37.5, 126.9, null, null)));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catchphrase").isNotEmpty());
    }

    @Test
    void 없는_장소면_404_TOUR_003() throws Exception {
        tourApiClient.respondDetail(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR-003"));
    }
}
