package com.offway.core.trip.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.StubTourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RegionRecommendIntegrationTest {

    private static final String URL = "/api/v1/regions/recommendations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourDataLabClient dataLabClient;

    @Autowired
    private StubTourApiClient tourApiClient;

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

    /** 볼거리가 충분한(확장 안 하는) 지역 콘텐츠 한 건 — 대표 이미지·categories(NA→관광지) 포함. */
    private static TourPoiResult sufficientContent() {
        TourPoi poi = new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null);
        return new TourPoiResult(List.of(poi), 38);
    }

    @Test
    void 도달_가능한_지역을_랭킹순으로_콘텐츠와_혜택뱃지와_함께_추천한다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty); // 방문자 데이터 없음 → 89 모두 동점(LOW), regionId 오름차순
        tourApiClient.respond(RegionRecommendIntegrationTest::sufficientContent);

        // 서울 출발 + 도달 한계 아주 크게 → 89 전부 도달 가능, 상위 20건만 콘텐츠 붙여 노출
        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.regions.length()").value(20)) // 후보 상한
                .andExpect(jsonPath("$.data.regions[0].name").exists())
                .andExpect(jsonPath("$.data.regions[0].crowdLevel").value("LOW"))
                .andExpect(jsonPath("$.data.regions[0].imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.regions[0].contentCount").value(38))
                .andExpect(jsonPath("$.data.regions[0].neighborIncluded").value(false))
                .andExpect(jsonPath("$.data.regions[0].categories[0].key").value("SIGHT"))
                // 전 지역이 인구감소지역 → 반값여행(운영기간 내) 뱃지가 붙는다
                .andExpect(jsonPath("$.data.regions[0].benefits[0].text").value("여행경비 50% 환급"));
    }

    @Test
    void 무드칩을_지정하면_그_볼거리가_있는_지역만_통과해도_결과가_내려온다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        // 모든 지역이 맛집(FD) 볼거리를 가진 것으로 stub → mood=FOOD 로 필터해도 결과가 유지된다
        TourPoi food = new TourPoi("200", 39, "FD", "완도 전복집", "전남 완도군", 34.3, 126.7, null, null);
        tourApiClient.respond(() -> new TourPoiResult(List.of(food), 20));

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000,
                  "mood": "FOOD" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(20))
                .andExpect(jsonPath("$.data.regions[0].categories[0].key").value("FOOD"));
    }

    @Test
    void 볼거리가_부족하면_인접_50km_지역을_묶어_확장한다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        // 모든 지역 볼거리 2개(충분 기준 9 미만) → 인접 50km 지역 콘텐츠로 확장(neighborIncluded)
        TourPoi few = new TourPoi("1", 12, "NA", "작은 볼거리", "강원", 37.4, 128.8, "http://img/n.jpg", null);
        tourApiClient.respond(() -> new TourPoiResult(List.of(few), 2));

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(20))
                // 상위 후보(부산 3구·강원 클러스터 등)엔 50km 내 이웃이 있어 확장이 일어난다
                .andExpect(jsonPath("$.data.regions[?(@.neighborIncluded == true)]").isNotEmpty());
    }

    @Test
    void 도달_한계가_아주_작으면_추천이_비어있다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 1 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(0));
    }

    @Test
    void 좌표가_범위를_벗어나면_400() throws Exception {
        String body = """
                { "originLat": 200.0, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 420 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 관광빅데이터_조회_실패는_502_TOUR_002() throws Exception {
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("TOUR-002"));
    }
}
