package com.offway.core.trip.controller;

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
class CategoryIntegrationTest {

    /** 시드된 인구감소지역 수(행안부 고시) — {@code ALL} 칩의 개수와 같다. */
    private static final int SEEDED_REGIONS = 89;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 필터칩_카테고리를_ALL부터_순서대로_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.categories.length()").value(5))
                .andExpect(jsonPath("$.data.categories[0].key").value("ALL"))
                .andExpect(jsonPath("$.data.categories[0].label").value("전체"))
                .andExpect(jsonPath("$.data.categories[1].key").value("SIGHT"))
                .andExpect(jsonPath("$.data.categories[1].label").value("관광지"))
                .andExpect(jsonPath("$.data.categories[2].key").value("STAY"))
                .andExpect(jsonPath("$.data.categories[2].label").value("숙박"))
                .andExpect(jsonPath("$.data.categories[3].key").value("EXPERIENCE"))
                .andExpect(jsonPath("$.data.categories[3].label").value("체험"))
                .andExpect(jsonPath("$.data.categories[4].key").value("FOOD"))
                .andExpect(jsonPath("$.data.categories[4].label").value("맛집"));
    }

    /**
     * 칩마다 지역 수를 함께 준다(#266) — 없으면 화면이 개수를 지어낸다("전부 1건").
     *
     * <p>{@code ALL} 만 단언한다. 나머지 칩은 적재된 지역 콘텐츠에 따라 달라지는 값이라 여기서 잠그면 콘텐츠와 함께 흔들린다 —
     * 필터 결과와 개수가 일치하는지는 콘텐츠를 직접 적재하는 {@code RegionListIntegrationTest} 가 소유한다.
     */
    @Test
    void 칩마다_지역_수를_함께_준다() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].key").value("ALL"))
                .andExpect(jsonPath("$.data.categories[0].regionCount").value(SEEDED_REGIONS))
                .andExpect(jsonPath("$.data.categories[1].regionCount").exists());
    }
}
