package com.offway.core.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.infrastructure.holiday.HolidayClient;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import java.time.LocalDate;
import java.util.Set;
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
class LeaveSandwichIntegrationTest {

    private static final String URL = "/api/v1/leaves/sandwich";

    // 2026-05: 5/1(금·노동절)·5/5(화·어린이날) 공휴일 → 5/4(월) 하나로 5/1~5/5 5일 휴식
    private static final Set<LocalDate> MAY_HOLIDAYS = Set.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5));

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
    void 노동절과_어린이날_사이_5월4일_연차를_추천한다() throws Exception {
        holidayClient.respond((year, month) -> MAY_HOLIDAYS);

        mockMvc.perform(get(URL).param("fromDate", "2026-05-01").param("months", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].leaveDates[0]").value("2026-05-04"))
                .andExpect(jsonPath("$.data.items[0].totalRestDays").value(5))
                .andExpect(jsonPath("$.data.items[0].efficiency").value("1일=5일"))
                .andExpect(jsonPath("$.data.items[0].window.start").value("2026-05-01"))
                .andExpect(jsonPath("$.data.items[0].window.end").value("2026-05-05"));
    }

    @Test
    void 남은_연차보다_많이_필요한_연휴는_제외한다() throws Exception {
        holidayClient.respond((year, month) -> MAY_HOLIDAYS);

        // 위 연휴는 연차 1일 필요 → remainingLeave=0 이면 추천에서 빠진다
        mockMvc.perform(get(URL)
                        .param("fromDate", "2026-05-01")
                        .param("months", "1")
                        .param("remainingLeave", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 공휴일이_없는_기간은_추천이_비어있다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        mockMvc.perform(get(URL).param("fromDate", "2026-05-01").param("months", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 조회_개월수가_범위를_벗어나면_400_LEAVE_003() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        mockMvc.perform(get(URL).param("fromDate", "2026-05-01").param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-003"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fromDate가_없으면_400_COMMON_400() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        mockMvc.perform(get(URL).param("months", "6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 공휴일_조회_실패는_502_HOLIDAY_001() throws Exception {
        holidayClient.respond((year, month) -> {
            throw HolidayException.lookupFailed(new RuntimeException("upstream down"));
        });

        mockMvc.perform(get(URL).param("fromDate", "2026-05-01").param("months", "1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("HOLIDAY-001"));
    }
}
