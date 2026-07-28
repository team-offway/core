package com.offway.core.weather.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.infrastructure.airkorea.AirKoreaClient;
import java.util.Optional;
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
class AirIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        AirKoreaClient stubAirKoreaClient() {
            // 정식 시도명이 축약명으로 매핑돼 넘어오는지도 함께 검증
            return sido -> "강원".equals(sido)
                    ? Optional.of(new AirQuality(25, 15, AirGrade.MODERATE))
                    : Optional.empty();
        }
    }

    @Test
    void 지역_대기질을_200으로_내려준다() throws Exception {
        mockMvc.perform(get("/api/v1/air").param("region", "강원특별자치도"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.pm10").value(25))
                .andExpect(jsonPath("$.data.pm25").value(15))
                .andExpect(jsonPath("$.data.grade").value("MODERATE"))
                .andExpect(jsonPath("$.data.gradeLabel").value("보통"));
    }

    @Test
    void 데이터가_없으면_200에_data는_null이다() throws Exception {
        mockMvc.perform(get("/api/v1/air").param("region", "제주특별자치도"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue())); // 계약대로 data=null
    }
}
