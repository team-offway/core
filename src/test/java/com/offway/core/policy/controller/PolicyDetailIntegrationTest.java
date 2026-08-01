package com.offway.core.policy.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class PolicyDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 반값여행_상세는_정책정보와_되는지역89를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.type").value("REGIONAL_VOUCHER"))
                .andExpect(jsonPath("$.data.badgeText").value("여행경비 50% 환급"))
                .andExpect(jsonPath("$.data.period.start").value("2026-04-01"))
                .andExpect(jsonPath("$.data.period.end").value("2026-08-31"))
                .andExpect(jsonPath("$.data.regions.length()").value(89))
                .andExpect(jsonPath("$.data.regions[?(@.name == '완도군 · 전라남도')]", hasSize(1)));
    }

    @Test
    void 미검증_정책은_숨겨져_404() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", 2)) // 디지털관광주민증 verified=false
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POLICY-001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 없는_정책은_404() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY-001"));
    }
}
