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
}
