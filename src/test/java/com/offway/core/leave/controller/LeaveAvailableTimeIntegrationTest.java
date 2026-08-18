package com.offway.core.leave.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.leave.domain.HolidayException;
import com.offway.core.leave.infrastructure.holiday.StubHolidayClient;
import com.offway.core.leave.service.LeaveService;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class LeaveAvailableTimeIntegrationTest {

    private static final String URL = "/api/v1/leaves/available-time";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubHolidayClient holidayClient;

    @Autowired
    private LeaveService leaveService;

    // 공휴일 캐시는 공유 싱글톤 — 각 테스트가 자기 stub 시나리오를 타도록 비운다(DB 롤백에 준하는 격리).
    // 비우지 않으면 앞 테스트의 공휴일이 살아남아 조회 실패 시나리오가 성공으로 응답한다.
    @BeforeEach
    void evictHolidayCache() {
        leaveService.evictCache();
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        StubHolidayClient stubHolidayClient() {
            return new StubHolidayClient();
        }
    }

    @Test
    void 평일_2박3일_자차면_소모연차3_도달240분을_내려준다() throws Exception {
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
                .andExpect(jsonPath("$.data.maxReachMinutes").value(240));
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

    /**
     * 도달 한계는 <b>이동수단에 따라 달라지지 않는다</b>(#289).
     *
     * <p>분 예산은 여행이 정하는 값이고, 수단은 그 시간에 얼마나 멀리 가는지에만 관여한다. 예전에는 여기서도
     * 0.7 을 곱해 대중교통이 <b>감쇠를 두 번</b> 받았고, 당일 추천이 서울 기준 89곳 중 3곳까지 줄었다.
     */
    @Test
    void 도달한계는_이동수단에_따라_달라지지_않는다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String transit = """
                { "startDate": "2026-05-06", "endDate": "2026-05-06", "transport": "TRANSIT" }""";
        String car = """
                { "startDate": "2026-05-06", "endDate": "2026-05-06", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDays").value(1))
                .andExpect(jsonPath("$.data.maxReachMinutes").value(120));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(car))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxReachMinutes").value(120));
    }

    /**
     * 첫날에 늦게 떠나면 도달 한계가 깎인다(#289).
     *
     * <p>여행일수 3일이지만 15시 출발이라 240분이 아니라 120분이다. 이 축이 없을 때 반반차로 떠나도
     * 7시간 거리를 추천해 <b>밤 10시 도착</b>이 나왔다.
     */
    @Test
    void 반반차로_떠나면_2박3일이어도_도달한계가_120분이다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", \
                "transport": "CAR", "startDayLeave": "QUARTER_DAY" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDays").value(3))
                .andExpect(jsonPath("$.data.maxReachMinutes").value(120))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(2.25));
    }

    @Test
    void 날짜_직접_모드도_확정된_날짜를_되돌려준다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-05-06"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-08"));
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
    void 이동수단이_없으면_400_COMMON_400() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08" }"""; // transport 누락

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

    @Test
    void 공휴일은_한_번만_조회하고_캐시에서_재사용한다() throws Exception {
        // 같은 달을 두 번 요청 — 캐시가 없으면 외부 호출이 두 번 난다(성능 규약 "요청 경로에서 외부 I/O 를 뺀다").
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        holidayClient.respond((year, month) -> {
            calls.incrementAndGet();
            return Set.of();
        });

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        int afterFirst = calls.get();
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(
                afterFirst, calls.get(), "두 번째 요청은 캐시에서 답해야 한다 — 외부 호출이 늘면 안 된다");
    }

    // ── 기간스타일 모드 (#46) ──────────────────────────────────────
    // 2026-05: 04(월) 05(화) 06(수) 07(목) 08(금) 09(토) 10(일) 11(월)

    @Test
    void 당일치기_스타일은_가장_가까운_쉬는_날을_확정해_연차를_쓰지_않는다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        // 05-04(월) 기준 → 다가오는 토요일 05-09
        String body = """
                { "periodStyle": "DAY_TRIP", "baseDate": "2026-05-04", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.startDate").value("2026-05-09"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-09"))
                .andExpect(jsonPath("$.data.travelDays").value(1))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(0.0))
                .andExpect(jsonPath("$.data.maxReachMinutes").value(120));
    }

    @Test
    void 주말포함_스타일은_금토일을_확정해_연차_하루만_쓴다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "WEEKEND", "baseDate": "2026-05-04", "weekendBridge": "FRIDAY",
                  "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-05-08"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-10"))
                .andExpect(jsonPath("$.data.travelDays").value(3))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(1.0)) // 금요일만
                .andExpect(jsonPath("$.data.maxReachMinutes").value(240));
    }

    @Test
    void 주말포함_월요일_브릿지는_토일월을_확정한다() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "WEEKEND", "baseDate": "2026-05-04", "weekendBridge": "MONDAY",
                  "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-05-09"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-11"))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(1.0)); // 월요일만
    }

    @Test
    void 연차만_스타일은_해석된_구간의_공휴일을_연차에서_빼준다() throws Exception {
        holidayClient.respond((year, month) -> Set.of(LocalDate.of(2026, 5, 5))); // 어린이날(화)

        // 05-04(월) 기준 3일 → 월·화·수. 화요일이 공휴일이라 연차는 2일만 빠진다(샌드위치 자동 반영, 결정 #38).
        String body = """
                { "periodStyle": "CONNECTED", "baseDate": "2026-05-04", "leaveDays": 3, "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-05-04"))
                .andExpect(jsonPath("$.data.endDate").value("2026-05-06"))
                .andExpect(jsonPath("$.data.travelDays").value(3))
                .andExpect(jsonPath("$.data.consumedLeaveDays").value(2.0));
    }

    @Test
    void 날짜와_기간스타일을_함께_보내면_400_LEAVE_004() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "endDate": "2026-05-08", "periodStyle": "DAY_TRIP",
                  "baseDate": "2026-05-04", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-004"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 날짜를_한쪽만_보내면_모드가_안_정해져_400_LEAVE_004() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "startDate": "2026-05-06", "transport": "CAR" }"""; // endDate 누락

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-004"));
    }

    @Test
    void 기간스타일에_기준일이_없으면_400_LEAVE_008() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "DAY_TRIP", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-008"));
    }

    @Test
    void 주말포함인데_브릿지_요일이_없으면_400_LEAVE_005() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "WEEKEND", "baseDate": "2026-05-04", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-005"));
    }

    @Test
    void 날짜를_한쪽만_보내며_기간스타일도_함께_보내면_400_LEAVE_004() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        // startDate 하나만으로는 날짜 모드가 성립하지 않지만, 스타일 모드도 날짜가 섞여 성립하지 않는다.
        // 이걸 통과시키면 보낸 startDate 가 조용히 버려진다.
        String body = """
                { "startDate": "2026-05-06", "periodStyle": "DAY_TRIP", "baseDate": "2026-05-04",
                  "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-004"));
    }

    @Test
    void 연차만인데_연차_일수가_없으면_400_LEAVE_006() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "CONNECTED", "baseDate": "2026-05-04", "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-006"));
    }

    @Test
    void 연차만인데_연차_일수가_범위_밖이면_400_LEAVE_007() throws Exception {
        holidayClient.respond((year, month) -> Set.of());

        String body = """
                { "periodStyle": "CONNECTED", "baseDate": "2026-05-04", "leaveDays": 5, "transport": "CAR" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEAVE-007"));
    }
}
