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

/**
 * 지역 장소 목록 API 계약(#144).
 *
 * <p>읽는 데이터는 {@code src/test/resources/data/place-pool.csv.gz} 의 소량 풀이다(의성군 = 76).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RegionPlacesIntegrationTest {

    private static final String URL = "/api/v1/regions/{regionId}/places";
    private static final long UISEONG = 76L;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 숙소_목록을_페이지_정보와_함께_내린다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "STAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.kind").value("STAY"))
                .andExpect(jsonPath("$.data.kindLabel").value("숙소"))
                .andExpect(jsonPath("$.data.places").isNotEmpty())
                .andExpect(jsonPath("$.data.places[0].id").exists())
                .andExpect(jsonPath("$.data.places[0].name").exists())
                .andExpect(jsonPath("$.data.places[0].categoryLabel").exists())
                .andExpect(jsonPath("$.pageResponse.page").value(0))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(3));
    }

    /** 화면이 필터 칩을 그리려면 그 종류에 어떤 분류가 있는지 알아야 한다. */
    @Test
    void 종류에_속한_분류_목록을_함께_내린다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[?(@.code == 'COFFEE')].label").value("커피"))
                .andExpect(jsonPath("$.data.categories[?(@.code == 'TRADITIONAL_TEA')]").isNotEmpty());
    }

    @Test
    void 분류로_좁혀_조회한다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "STAY").param("category", "HANOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places.length()").value(1))
                .andExpect(jsonPath("$.data.places[0].name").value("우경고택"))
                .andExpect(jsonPath("$.data.places[0].categoryLabel").value("한옥체험"));
    }

    /** 코스 적합도가 높은 분류를 앞에 둔다 — 첫 화면에 다방·패스트푸드가 깔리면 "볼 게 없다" 로 읽힌다. */
    @Test
    void 적합도가_높은_분류가_먼저_나온다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "STAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places[0].category").value("HANOK"));
    }

    @Test
    void 페이지_크기를_지정할_수_있다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "FOOD").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places.length()").value(2))
                .andExpect(jsonPath("$.pageResponse.size").value(2))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(2));
    }

    /** 지역당 수천 건이라 상한이 없으면 한 요청이 전부를 끌어온다. */
    @Test
    void 페이지_크기_상한을_넘겨도_상한까지만_준다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "FOOD").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.size").value(100));
    }

    @Test
    void 분류가_종류에_안_맞으면_400_TRIP_001() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "STAY").param("category", "COFFEE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP-001"));
    }

    @Test
    void 없는_종류를_보내면_400이다() throws Exception {
        mockMvc.perform(get(URL, UISEONG).param("kind", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    /** 데이터가 없는 지역은 빈 목록이다 — 없는 것을 지어내지 않고, 오류도 아니다. */
    @Test
    void 데이터가_없는_지역은_빈_목록이다() throws Exception {
        mockMvc.perform(get(URL, 1L).param("kind", "STAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.places").isEmpty())
                .andExpect(jsonPath("$.pageResponse.totalElements").value(0));
    }
}
