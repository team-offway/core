package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
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

/**
 * 관광 API 가 못 채운 풀을 인허가 데이터가 메우는지 확인한다(#144).
 *
 * <p><b>이 작업의 이유가 그대로 시나리오다.</b> TourAPI 숙박은 관광사업체 위주라 지방 숙소가 거의 없고(의성군 1건),
 * 한도가 소진되면 아예 빈다. 그때 코스가 어떻게 되는지가 여기서 갈린다 — 예전에는 슬롯이 조용히 빠져
 * "잘 곳 없는 2박3일" 이 200 으로 나갔다.
 *
 * <p>이 클래스가 읽는 풀은 {@code src/test/resources/data/place-pool.csv.gz} 의 소량 데이터다(의성군 = 76).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseSupplementIntegrationTest {

    private static final String URL = "/api/v1/courses/generate";

    /** 테스트 풀이 채운 지역 — 경상북도 의성군. */
    private static final String UISEONG = "76";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }

        @Bean
        @Primary
        KmaWeatherClient stubKmaWeatherClient() {
            return new StubKmaWeatherClient();
        }
    }

    private static String body(String regionId) {
        return """
                { "regionId": %s, "travelDays": 2, "density": "RELAXED", "transport": "CAR",
                  "originLat": 36.35, "originLng": 128.69, "travelDate": "2026-09-04" }"""
                .formatted(regionId);
    }

    /** 볼거리만 있고 숙박은 없는 응답 — TourAPI 가 지방에서 실제로 보이는 모습이다. */
    private static TourPoiResult sightsOnly() {
        List<TourPoi> items = List.of(
                poi("s0", 12, 36.35, 128.69),
                poi("s1", 12, 36.36, 128.70),
                poi("s2", 12, 36.37, 128.71));
        return new TourPoiResult(items, items.size());
    }

    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, "NA", "장소" + id, "경북 의성군", lat, lng, null, null);
    }

    @Test
    void 관광API가_숙박을_못_주면_인허가_숙소로_채운다() throws Exception {
        tourApiClient.respond(CourseSupplementIntegrationTest::sightsOnly);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(UISEONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                // 1박2일이면 숙박 슬롯이 하나 있어야 한다 — 예전에는 여기가 통째로 비었다
                .andExpect(jsonPath("$.data.days[0].items[?(@.kind == 'STAY')]").isNotEmpty())
                // 그 자리가 인허가 숙소로 채워졌는지까지 본다. 슬롯 존재만 보면 TourAPI 쪽 후보가
                // 어쩌다 들어와도 통과해, 정작 검증하려던 보충 경로를 놓친다.
                .andExpect(jsonPath("$.data.days[0].items[?(@.kind == 'STAY')].poiContentId")
                        .value(hasItem(startsWith("LIC-"))));
    }

    /** 오늘 실제로 겪은 상황 — 한도가 소진돼 TourAPI 가 아무것도 못 줄 때. */
    @Test
    void 관광API가_통째로_비어도_코스가_나온다() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(UISEONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.days.length()").value(2));
    }

    /** 인허가 후보는 상세 조회 대상이 아니므로 식별자로 출처를 구분할 수 있어야 한다. */
    @Test
    void 인허가_후보는_식별자로_구분된다() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(UISEONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").value(startsWith("LIC-")));
    }

    /** 풀이 아예 없는 지역까지 구제하지는 않는다 — 없는 것을 지어내면 안 된다. */
    @Test
    void 인허가_데이터도_없는_지역은_여전히_404다() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-001"));
    }

    // ── 상세 조회(#144) ───────────────────────────────────────────

    /**
     * 코스에 실린 인허가 장소를 눌렀을 때 — TourAPI 에 물으면 없는 콘텐츠라 404 다. 백엔드가 우리 DB 로 답한다.
     */
    @Test
    void 인허가_장소의_상세는_우리_DB가_답한다() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        // 코스에 실제로 실린 식별자를 그대로 눌러본다
        String poiId = objectMapper.readTree(
                        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(UISEONG)))
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("days").get(0).path("items").get(0).path("poiContentId").asText();

        mockMvc.perform(get("/api/v1/pois/{id}", poiId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.contentId").value(poiId))
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.address").isNotEmpty());
    }

    @Test
    void 없는_인허가_식별자는_404다() throws Exception {
        mockMvc.perform(get("/api/v1/pois/{id}", "LIC-99999999"))
                .andExpect(status().isNotFound());
    }
}
