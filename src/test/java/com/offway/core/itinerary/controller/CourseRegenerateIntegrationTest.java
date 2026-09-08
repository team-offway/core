package com.offway.core.itinerary.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.repository.UnroutableProbeJpaRepository;
import com.offway.core.transport.service.UnroutableCoordinateService;
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

    @Autowired
    private UnroutableCoordinateService unroutableCoordinateService;

    @Autowired
    private UnroutableProbeJpaRepository unroutableProbeJpaRepository;

    /**
     * 이 클래스는 트랜잭션 롤백이 없다(재생성이 쓰기 경로가 아니라 굳이 걸지 않았다). 차단 좌표만은 DB 에
     * 남는 쓰기라, 지우지 않으면 다음 테스트의 후보에서 조용히 장소가 빠진다.
     */
    @org.junit.jupiter.api.AfterEach
    void clearUnroutableProbes() {
        unroutableProbeJpaRepository.deleteAll();
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    /**
     * 콘텐츠 타입에 <b>맞는 대분류</b>를 함께 준다.
     *
     * <p>예전에는 대분류를 {@code "NA"} 로 고정했다. 풀을 콘텐츠 타입으로 가르던 시절엔 문제가 없었지만,
     * 이제 대분류가 기준이라 <b>타입 39(음식점)인데 대분류가 자연</b>인 후보가 볼거리로 들어간다 —
     * 실제 응답에는 없는 조합이다(전수 6,821건 확인, 어긋난 건 0건).
     */
    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, lclsOf(contentTypeId), "장소" + id, "부산 동구", lat, lng,
                "http://img/" + id + ".jpg", null, null);
    }

    private static String lclsOf(int contentTypeId) {
        return switch (contentTypeId) {
            case 39 -> "FD";
            case 32 -> "AC";
            default -> "NA";
        };
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

    // ── 후보 필터가 재생성 경로에도 걸린다 (#335) ──────────────────────────

    /** 볼거리 하나를 이 좌표에 두고 차단한다 — 다른 후보와 겹치지 않는 자리다. */
    private static final Coordinate UNROUTABLE = new Coordinate(36.90, 128.90);

    /** 같은 점에 몰린 볼거리 — 알펜시아 리조트(운영 코스 67 3일차가 그랬다). */
    private static final Coordinate SAME_SPOT = new Coordinate(37.6541478, 128.652815);

    private static TourPoiResult poisPlus(List<TourPoi> extra) {
        List<TourPoi> items = new ArrayList<>(pois(SCARCE_SIGHTS).items());
        items.addAll(extra);
        return new TourPoiResult(items, items.size());
    }

    private void block(Coordinate point) {
        // 서로 다른 짝으로 두 번 — 그래야 "옆에 있었을 뿐인 좌표" 와 갈린다.
        unroutableCoordinateService.report(new Coordinate(35.90, 129.90), point, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(point, new Coordinate(35.95, 129.95), UnroutableReason.NO_ROAD_LINK);
    }

    /**
     * 재생성의 씨앗 판정({@code selectedSightIds})과 실제 조립이 <b>같은 후보</b>를 봐야 한다. 판정만 거르지
     * 않으면 실제 코스에 없는 장소를 세어, "충분히 다르다" 는 답과 화면에 뜨는 코스가 어긋난다.
     *
     * <p>여기서는 사용자에게 보이는 쪽을 잠근다 — 차단된 좌표의 장소가 재생성 코스에 없다는 것.
     * {@code overlapRatio} 숫자 자체는 단언하지 않는다(의도적 생략): 그러려면 판정 로직을 테스트가 다시
     * 구현해야 하고, 그러면 계약이 아니라 구현을 베끼는 테스트가 된다.
     */
    @Test
    void 차단된_좌표는_재생성_코스에도_안_들어간다() throws Exception {
        block(UNROUTABLE);
        tourApiClient.respond(() -> poisPlus(List.of(poi("bad", 12, UNROUTABLE.lat(), UNROUTABLE.lng()))));

        assertFalse(placesOf(call(REGENERATE, ""), "course.").contains("bad"));
    }

    @Test
    void 같은_좌표의_볼거리는_재생성_코스에도_하나만_들어간다() throws Exception {
        tourApiClient.respond(() -> poisPlus(List.of(
                poi("a0", 12, SAME_SPOT.lat(), SAME_SPOT.lng()),
                poi("a1", 12, SAME_SPOT.lat(), SAME_SPOT.lng()),
                poi("a2", 12, SAME_SPOT.lat(), SAME_SPOT.lng()))));

        List<String> places = placesOf(call(REGENERATE, ""), "course.");

        assertEquals(1, places.stream().filter(id -> id.startsWith("a")).count(), "실제=" + places);
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
