package com.offway.core.itinerary.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.util.ArrayList;
import java.util.List;
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
 * 코스 재생성(#114) 통합 테스트.
 *
 * <p>생성이 결정론적이라는 게 이 기능의 출발점이다 — 그냥 다시 부르면 같은 코스가 나온다. 그래서 여기서 볼 것은
 * "정말 달라지는가" 와 "같은 씨앗이면 정말 같은가" 두 가지다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseRegenerateIntegrationTest {

    private static final String GENERATE = "/api/v1/courses/generate";
    private static final String REGENERATE = "/api/v1/courses/regenerate";

    /** 재생성 여지가 있으려면 후보가 필요 개수보다 넉넉해야 한다. */
    private static final int RICH_SIGHTS = 20;

    /** 필요 개수와 거의 같은 후보 — 다르게 짤 수 없는 지역을 흉내 낸다. */
    private static final int SCARCE_SIGHTS = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, "NA", "장소" + id, "부산 동구", lat, lng, "http://img/" + id + ".jpg", null);
    }

    /** 볼거리를 서로 떨어진 곳에 흩어 둔다 — 씨앗이 다르면 다른 군집이 잡히도록. */
    private static TourPoiResult pois(int sights) {
        List<TourPoi> items = new ArrayList<>();
        for (int i = 0; i < sights; i++) {
            items.add(poi("s" + i, 12, 35.10 + i * 0.05, 129.03 + i * 0.05));
        }
        for (int i = 0; i < 4; i++) {
            items.add(poi("f" + i, 39, 35.11 + i * 0.05, 129.04 + i * 0.05));
        }
        items.add(poi("st0", 32, 35.10, 129.03));
        return new TourPoiResult(items, items.size());
    }

    private static String body(String extra) {
        return """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01"%s }"""
                .formatted(extra.isEmpty() ? "" : ", " + extra);
    }

    private String call(String url, String extra) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body(extra)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 코스가 담은 장소들 — 순서가 아니라 구성이 달라져야 "다른 코스" 다. */
    private static List<String> placesOf(String json, String coursePath) {
        return JsonPath.read(json, "$.data." + coursePath + "days[*].items[*].poiContentId");
    }

    @Test
    void 재생성하면_기존과_다른_코스가_나온다() throws Exception {
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        List<String> first = placesOf(call(GENERATE, ""), "");
        String regenerated = call(REGENERATE, "");

        assertNotEquals(
                java.util.Set.copyOf(first),
                java.util.Set.copyOf(placesOf(regenerated, "course.")),
                "재생성인데 장소 구성이 같으면 사용자는 버튼이 고장 난 줄 안다");
        assertTrue(Boolean.TRUE.equals(JsonPath.read(regenerated, "$.data.differentFromPrevious")),
                "후보가 넉넉하면 다르게 만들 수 있어야 한다");
    }

    @Test
    void 같은_씨앗이면_같은_코스가_나온다() throws Exception {
        // 재현성 — 같은 seed 로 문의가 들어오면 같은 코스를 다시 만들어 볼 수 있어야 한다.
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        String once = call(REGENERATE, "\"seed\": 12345");
        String twice = call(REGENERATE, "\"seed\": 12345");

        assertEquals(placesOf(once, "course."), placesOf(twice, "course."));
        assertEquals(12345, ((Number) JsonPath.read(once, "$.data.seed")).longValue(),
                "지정한 씨앗을 그대로 써야 한다 — 다르게 만들려고 바꿔 버리면 재현이 목적인 호출의 뜻이 뒤집힌다");
    }

    @Test
    void 다른_씨앗이면_다른_코스가_나온다() throws Exception {
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        List<String> withOne = placesOf(call(REGENERATE, "\"seed\": 1"), "course.");
        List<String> withOther = placesOf(call(REGENERATE, "\"seed\": 7777"), "course.");

        assertNotEquals(java.util.Set.copyOf(withOne), java.util.Set.copyOf(withOther));
    }

    @Test
    void 제외한_장소는_코스에_들어가지_않는다() throws Exception {
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        String regenerated = call(REGENERATE, "\"excludePoiContentIds\": [\"s0\", \"s1\", \"s2\"]");

        List<String> places = placesOf(regenerated, "course.");
        assertTrue(places.stream().noneMatch(List.of("s0", "s1", "s2")::contains),
                "빼달라고 한 장소가 다시 나오면 안 된다. 실제=" + places);
    }

    @Test
    void 응답의_씨앗을_다음_previousSeed로_넘기면_또_다른_코스가_나온다() throws Exception {
        // 화면이 계속 "다시 추천받기" 를 눌러도 매번 달라져야 한다.
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        String firstRun = call(REGENERATE, "");
        long seed = ((Number) JsonPath.read(firstRun, "$.data.seed")).longValue();
        String secondRun = call(REGENERATE, "\"previousSeed\": " + seed);

        assertNotEquals(
                java.util.Set.copyOf(placesOf(firstRun, "course.")),
                java.util.Set.copyOf(placesOf(secondRun, "course.")));
    }

    @Test
    void 같은_previousSeed로_두_번_부르면_같은_코스가_나온다() throws Exception {
        // 씨앗을 맡겼을 때도 결과는 입력의 함수여야 한다.
        //
        // 예전에는 후보 씨앗을 전역 난수로 뽑아 같은 요청이 매번 다른 답을 냈다. 그러면 FE 가 네트워크 재시도로
        // 같은 요청을 두 번 보냈을 때 화면이 이유 없이 바뀌고, 위의 "또 다른 코스" 테스트도 운에 따라 통과했다
        // (CI 에서 실제로 간헐 실패했다). 무작위성을 없앤 것을 여기서 못 박는다.
        tourApiClient.respond(() -> pois(RICH_SIGHTS));

        String once = call(REGENERATE, "\"previousSeed\": 4242");
        String twice = call(REGENERATE, "\"previousSeed\": 4242");

        assertEquals(placesOf(once, "course."), placesOf(twice, "course."));
        assertEquals(
                ((Number) JsonPath.read(once, "$.data.seed")).longValue(),
                ((Number) JsonPath.read(twice, "$.data.seed")).longValue(),
                "고른 씨앗까지 같아야 한다 — 여기가 흔들리면 코스가 같은 것도 우연이다");
    }

    @Test
    void 후보가_모자라면_같은_코스라도_주되_달라지지_않았다고_알린다() throws Exception {
        // 인구감소지역은 볼거리가 필요 개수와 비슷한 경우가 흔하다. 조용히 같은 코스를 주면 안 된다.
        tourApiClient.respond(() -> pois(SCARCE_SIGHTS));

        mockMvc.perform(post(REGENERATE).contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.course.days.length()").value(2))
                .andExpect(jsonPath("$.data.differentFromPrevious").value(false))
                .andExpect(jsonPath("$.data.overlapRatio").value(1.0));
    }

    @Test
    void 제외가_지나쳐_볼거리가_남지_않으면_404다() throws Exception {
        tourApiClient.respond(() -> pois(SCARCE_SIGHTS));

        mockMvc.perform(post(REGENERATE).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"excludePoiContentIds\": [\"s0\", \"s1\", \"s2\", \"s3\"]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-001"));
    }

    @Test
    void 여러_씨앗을_시도해도_외부_후보조회는_한_번뿐이다() throws Exception {
        // 판정하자고 코스를 통째로 짜면 TourAPI 도, TMAP 경유지 최적화(일일 50건)도 시도 횟수만큼 곱해진다.
        // 씨앗 판정은 좌표 계산으로만 하고 조립은 이긴 씨앗 하나로 한 번만 해야 한다.
        tourApiClient.respond(() -> pois(RICH_SIGHTS));
        tourApiClient.resetAreaCallCount();

        call(REGENERATE, "");

        // 볼거리·맛집·숙박 세 풀을 각각 조회한다 = 후보 수집 1회분.
        assertEquals(3, tourApiClient.areaCallCount(),
                "재생성이 시도 횟수만큼 후보를 다시 모으면 외부 호출이 그 배수로 는다");
    }

    @Test
    void 씨앗을_지정해도_외부_후보조회는_한_번뿐이다() throws Exception {
        tourApiClient.respond(() -> pois(RICH_SIGHTS));
        tourApiClient.resetAreaCallCount();

        call(REGENERATE, "\"seed\": 12345");

        assertEquals(3, tourApiClient.areaCallCount());
    }
}
