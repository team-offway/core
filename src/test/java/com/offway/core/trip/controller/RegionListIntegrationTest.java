package com.offway.core.trip.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.datalab.StubTourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.service.RegionContentProvider;
import com.offway.core.trip.service.RegionContentRefreshService;
import com.offway.core.trip.service.RegionRankingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 지역 목록 API 계약(#266) — "더보기" 가 홈 상위 6곳 너머를 볼 수 있는지, 페이지 경계가 잠겨 있는지.
 *
 * <p>대상은 마이그레이션이 시드한 인구감소지역 89곳 전부다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RegionListIntegrationTest {

    private static final String URL = "/api/v1/regions";

    /** 시드된 인구감소지역 수(행안부 고시). 기본 페이지 20 기준 5페이지, 마지막 페이지 9건. */
    private static final int SEEDED_REGIONS = 89;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourDataLabClient dataLabClient;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private RegionRankingService regionRankingService;

    @Autowired
    private RegionContentProvider regionContentProvider;

    @Autowired
    private RegionContentRefreshService regionContentRefreshService;

    // 랭킹·콘텐츠 캐시는 공유 싱글톤 — 각 테스트가 자기 stub 시나리오를 타도록 비운다(DB 롤백에 준하는 격리).
    @BeforeEach
    void evictCaches() {
        regionRankingService.evictCache();
        regionContentProvider.evictCache();
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourDataLabClient stubTourDataLabClient() {
            return new StubTourDataLabClient();
        }

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    /** 볼거리가 충분한 지역 콘텐츠 — 대표 이미지·categories(NA → 관광지). 89곳이 전부 같은 값을 받는다. */
    private static TourPoiResult content() {
        TourPoi poi = new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null);
        return new TourPoiResult(List.of(poi), 38);
    }

    /** 콘텐츠를 stub 으로 적재한다 — 요청 경로는 저장된 값만 읽는다(#193). */
    private void loadContent() {
        dataLabClient.respond(TourVisitorResult::empty);
        tourApiClient.respond(RegionListIntegrationTest::content);
        regionContentRefreshService.refresh();
    }

    @Test
    void 첫_페이지를_페이지_메타와_함께_내린다() throws Exception {
        loadContent();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.detail").value("요청이 정상 처리되었습니다."))
                .andExpect(jsonPath("$.data.regions.length()").value(20))
                .andExpect(jsonPath("$.data.regions[0].regionId").exists())
                .andExpect(jsonPath("$.data.regions[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.regions[0].crowdLevel").value("LOW"))
                .andExpect(jsonPath("$.data.regions[0].imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.regions[0].contentCount").value(38))
                .andExpect(jsonPath("$.data.regions[0].categories[0].key").value("SIGHT"))
                .andExpect(jsonPath("$.data.regions[0].categories[0].label").value("관광지"))
                // 페이지 메타는 data 안이 아니라 공통 래퍼의 pageResponse 로 나간다(api-convention).
                .andExpect(jsonPath("$.data.page").doesNotExist())
                .andExpect(jsonPath("$.pageResponse.page").value(0))
                .andExpect(jsonPath("$.pageResponse.size").value(20))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(SEEDED_REGIONS))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(5));
    }

    /** 이 API 가 생긴 이유 그대로 — 더보기가 홈의 6곳이 아니라 <b>새 지역</b>을 줘야 한다. */
    @Test
    void 다음_페이지는_앞_페이지와_다른_지역을_준다() throws Exception {
        loadContent();

        String page0 = mockMvc.perform(get(URL).param("page", "0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String page1 = mockMvc.perform(get(URL).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.page").value(1))
                .andExpect(jsonPath("$.data.regions.length()").value(20))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotEquals(page0, page1);
    }

    @Test
    void 마지막_페이지는_남은_만큼만_준다() throws Exception {
        loadContent();

        mockMvc.perform(get(URL).param("page", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(SEEDED_REGIONS - 80))
                .andExpect(jsonPath("$.pageResponse.page").value(4))
                .andExpect(jsonPath("$.pageResponse.totalPages").value(5));
    }

    /** 무한 스크롤은 마지막 다음 페이지를 자연스럽게 한 번 더 요청한다 — 오류가 아니라 빈 목록이다. */
    @Test
    void 범위를_벗어난_페이지는_빈_목록이다() throws Exception {
        loadContent();

        mockMvc.perform(get(URL).param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regions").isEmpty())
                .andExpect(jsonPath("$.pageResponse.totalElements").value(SEEDED_REGIONS));
    }

    /** 잘못된 값은 거절하지 않고 자른다 — 400 으로 끊으면 화면이 통째로 빈다(Paging 규약). */
    @Test
    void 음수_페이지는_첫_페이지로_자른다() throws Exception {
        loadContent();

        mockMvc.perform(get(URL).param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.page").value(0))
                .andExpect(jsonPath("$.data.regions.length()").value(20));
    }

    /** 상한이 없으면 size=9999 한 번으로 페이지네이션이 없던 때와 같아진다. */
    @Test
    void 페이지_크기_상한을_넘겨도_상한까지만_준다() throws Exception {
        loadContent();

        mockMvc.perform(get(URL).param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageResponse.size").value(100))
                .andExpect(jsonPath("$.data.regions.length()").value(SEEDED_REGIONS));
    }

    @Test
    void 카테고리로_좁혀_조회한다() throws Exception {
        loadContent();

        // 적재된 콘텐츠가 전부 관광지(NA)라 SIGHT 는 89곳, 숙박은 0곳이다.
        mockMvc.perform(get(URL).param("category", "SIGHT").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(5))
                .andExpect(jsonPath("$.pageResponse.totalElements").value(SEEDED_REGIONS));

        mockMvc.perform(get(URL).param("category", "STAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions").isEmpty())
                .andExpect(jsonPath("$.pageResponse.totalElements").value(0));
    }

    /** 필터 결과와 칩 개수가 같은 판정을 쓰는지 — 어긋나면 "89곳" 칩을 눌렀는데 다른 수가 나온다. */
    @Test
    void 칩_개수는_그_카테고리로_좁힌_전체_건수와_같다() throws Exception {
        loadContent();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[?(@.key == 'ALL')].regionCount").value(SEEDED_REGIONS))
                .andExpect(jsonPath("$.data.categories[?(@.key == 'SIGHT')].regionCount").value(SEEDED_REGIONS))
                .andExpect(jsonPath("$.data.categories[?(@.key == 'STAY')].regionCount").value(0));
    }

    /**
     * 이 API 의 비용을 잠근다 — 89곳을 페이지로 끊어 주는데 페이지마다 외부를 부르면 더보기 몇 번으로 TourAPI 일일 한도가 마른다.
     *
     * <p>세는 대상은 <b>지역 콘텐츠 조회</b>다(지역당 자기 + 인접 최대 3곳이라 팬아웃이 가장 큰 호출). 관광빅데이터는 방문자 집계가
     * 통째로 비어 있을 때만 한 번 시도하는 별도 경로라 여기서 세지 않는다 — 홈·추천과 공유하는 기존 동작이다.
     */
    @Test
    void 목록_조회는_지역_콘텐츠_외부_호출을_한_번도_하지_않는다() throws Exception {
        loadContent();
        tourApiClient.resetAreaCallCount();

        mockMvc.perform(get(URL)).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("page", "1")).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("category", "SIGHT")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());

        assertEquals(0, tourApiClient.areaCallCount());
    }

    @Test
    void 없는_카테고리를_보내면_400이다() throws Exception {
        mockMvc.perform(get(URL).param("category", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
