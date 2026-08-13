package com.offway.core.policy.controller;

import static org.hamcrest.Matchers.containsString;
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
    void 반값여행_상세는_되는_지역_16곳만_반환한다() throws Exception {
        // 예전에는 89곳을 돌려줬다. 실제 시범사업은 16곳뿐이라, 그 목록을 보고 계획한 73곳 사용자는
        // 사전 신청 단계에서야 대상이 아님을 알게 된다(#217).
        mockMvc.perform(get("/api/v1/policies/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.type").value("REGIONAL_VOUCHER"))
                .andExpect(jsonPath("$.data.badgeText").value("여행경비 50% 환급"))
                .andExpect(jsonPath("$.data.period.start").value("2026-04-01"))
                .andExpect(jsonPath("$.data.period.end").value("2026-09-30"))
                .andExpect(jsonPath("$.data.regions.length()").value(16))
                .andExpect(jsonPath("$.data.regions[?(@.name == '완도군 · 전라남도')]", hasSize(1)))
                .andExpect(jsonPath("$.data.regions[?(@.name == '가평군 · 경기도')]", hasSize(0)));
    }

    @Test
    void 지자체별로_기간이_다르면_문구로_알린다() throws Exception {
        // 날짜만으로 다 말할 수 없는 정책이다. 그렇다고 날짜를 비우면 만료가 안 걸리므로(isActiveOn 에서
        // null 은 "상시"), 바깥 경계는 날짜로 두고 사정은 문구로 내린다(#217).
        mockMvc.perform(get("/api/v1/policies/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodNote").value(containsString("지자체별")));
    }

    @Test
    void 못_쓰는_숙소_유형은_혜택_설명에_실린다() throws Exception {
        // 기간 문구가 아니라 혜택 설명에 둔다 — 제외 유형은 언제 쓰느냐와 무관하다.
        // 이걸 안 알려주면 사용자가 캠핑장·외국인도시민박을 예약하고 쿠폰이 안 먹는 것을 그때 안다.
        mockMvc.perform(get("/api/v1/policies/{id}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.benefitDetail").value(containsString("외국인도시민박업 제외")))
                .andExpect(jsonPath("$.data.periodNote").value(containsString("선착순")));
    }

    @Test
    void 숙박세일페스타_상세는_비수도권_85곳을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("STAY_FESTA"))
                .andExpect(jsonPath("$.data.regions.length()").value(85))
                .andExpect(jsonPath("$.data.regions[?(@.name == '가평군 · 경기도')]", hasSize(0)));
    }

    @Test
    void 미검증_정책은_숨겨져_404() throws Exception {
        mockMvc.perform(get("/api/v1/policies/{id}", 3)) // 디지털관광주민증 verified=false
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
