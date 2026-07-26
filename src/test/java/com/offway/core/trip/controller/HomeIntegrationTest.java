package com.offway.core.trip.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeIntegrationTest {

    private static final String URL = "/api/v1/home";

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
    void 남은연차_필터칩_추천지역을_함께_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);

        mockMvc.perform(get(URL).param("remainingLeave", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(13))
                .andExpect(jsonPath("$.data.filters.length()").value(5))
                .andExpect(jsonPath("$.data.filters[0].key").value("ALL"))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6))
                .andExpect(jsonPath("$.data.recommendedRegions[0].name").exists())
                .andExpect(jsonPath("$.data.recommendedRegions[0].crowdLevel").value("LOW"))
                // 전 지역이 인구감소지역 → 대표 혜택으로 반값여행 뱃지
                .andExpect(jsonPath("$.data.recommendedRegions[0].benefit.text").value("여행경비 50% 환급"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].benefit.policyType").value("REGIONAL_VOUCHER"));
    }

    @Test
    void 남은연차가_없어도_200으로_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6));
    }

    @Test
    void 관광빅데이터_조회_실패는_502() throws Exception {
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });

        mockMvc.perform(get(URL).param("remainingLeave", "13"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TOUR-002"));
    }
}
