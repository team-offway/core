package com.offway.core.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LeaveAvailableTimeIntegrationTest {

    private static final String URL = "/api/v1/leaves/available-time";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubHolidayClient holidayClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        StubHolidayClient stubHolidayClient() {
            return new StubHolidayClient();
        }
    }

    @Test
    void 평일_2박3일_자차면_소모연차3_도달420분을_내려준다() throws Exception {
        holidayClient.respond((year, month) -> Set.of()); // 공휴일 없음

        // 2026-05-06(수)~08(금) — 평일 3일
        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.travelDays").value(3))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(3.0))
                .andExpect(jsonPath("$.data.maxReachMinutes").value(420));
    }

    @Test
    void 구간에_낀_공휴일은_소모연차에서_빠진다() throws Exception {
        holidayClient.respond((year, month) -> Set.of(LocalDate.of(2026, 5, 5))); // 어린이날(화)

        // 2026-05-05(화·공휴일)~07(목) — 화 무료, 수·목만 연차
        String body = """
                { "startDate": "2026-05-05", "endDate": "2026-05-07", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDays").value(3))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(2.0));
    }

    @Test
    void 대중교통이면_도달한계가_배율만큼_준다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-06", "transport": "TRANSIT" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDays").value(1))
                .andExpect(jsonPath("$.data.maxReachMinutes").value(84)); // 당일 자차 120 × 0.7
    }

    @Test
    void 종료일이_시작일보다_앞서면_400_LEAVE_001() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-08", "endDate": "2026-05-06", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("LEAVE-001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 여행구간이_2박3일을_넘으면_400_LEAVE_002() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        // 2026-05-04~07 = 4일
        String body = """
                { "startDate": "2026-05-04", "endDate": "2026-05-07", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-002"));
    }

    @Test
    void 필수값이_없으면_400_COMMON_400() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "transport": "CAR" }"""; // endDate 누락

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 공휴일_조회_실패는_502_HOLIDAY_001() throws Exception {
        holidayClient.respond((year, month) -> {
            throw HolidayException.lookupFailed(new RuntimeException("upstream down"));
        });

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("HOLIDAY-001"));
    }
}
