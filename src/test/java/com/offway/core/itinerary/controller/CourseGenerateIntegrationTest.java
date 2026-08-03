package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.service.TrainRouteService;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseGenerateIntegrationTest {

    private static final String URL = "/api/v1/courses/generate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private StubKmaWeatherClient weatherClient;

    @Autowired
    private StubTrainInfoClient trainInfoClient;

    @Autowired
    private TrainRouteService trainRouteService;

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

        @Bean
        @Primary
        TrainInfoClient stubTrainInfoClient() {
            return new StubTrainInfoClient();
        }
    }

    @AfterEach
    void resetWeatherStub() {
        weatherClient.reset(); // 공유 컨텍스트 — 앞 테스트가 세팅한 예보가 다음 테스트로 새지 않게
    }

    private static TourPoi poi(String id, int contentTypeId, double lat, double lng) {
        return new TourPoi(id, contentTypeId, "NA", "장소" + id, "부산 동구", lat, lng, "http://img/" + id + ".jpg", null);
    }

    private static TourPoiResult richPois() {
        List<TourPoi> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
        }
        items.add(poi("f0", 39, 35.11, 129.04));
        items.add(poi("f1", 39, 35.12, 129.05));
        items.add(poi("st0", 32, 35.10, 129.03));
        return new TourPoiResult(items, items.size());
    }

    @Test
    void 코스를_생성해_날짜별_타임라인과_혜택을_200으로_내린다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.travelDays").value(2))
                .andExpect(jsonPath("$.data.density").value("PACKED"))
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].order").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0))
                .andExpect(jsonPath("$.data.days[0].items[0].kind").exists())
                .andExpect(jsonPath("$.data.days[0].items[0].lat").exists())
                // 인구감소지역(부산 동구) + 시드 정책 기간 내 → 반값여행 혜택
                .andExpect(jsonPath("$.data.benefits[0].text").value("여행경비 50% 환급"));
    }

    @Test
    void 코스에_여행날짜_날씨를_함께_내린다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        LocalDate date = LocalDate.of(2026, 5, 1);
        weatherClient.respond(() -> Optional.of(new DailyWeather(date, 18, 27, SkyState.CLEAR, 20)));

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weather.date").value("2026-05-01"))
                .andExpect(jsonPath("$.data.weather.minTemp").value(18))
                .andExpect(jsonPath("$.data.weather.maxTemp").value(27))
                .andExpect(jsonPath("$.data.weather.sky").value("맑음"))
                .andExpect(jsonPath("$.data.weather.rainProbability").value(20));
    }

    @Test
    void 날씨_예보가_없으면_weather는_null이고_코스는_정상() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        // 예보 범위 밖·조회 실패 → 빈 예보(stub 기본값). 날씨는 부가 정보라 코스는 그대로 200
        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                .andExpect(jsonPath("$.data.weather").value(nullValue()));
    }

    @Test
    void 여행일수가_2박3일을_초과하면_400() throws Exception {
        String body = """
                { "regionId": 1, "travelDays": 4, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    void 볼거리가_없는_지역이면_404_ITINERARY_001() throws Exception {
        tourApiClient.respond(TourPoiResult::empty);

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-001"));
    }

    // ── 대중교통 코스의 도착 지점·도착 시각 반영 (#127) ────────────────────────────────
    //
    // 지역 1 = 부산 동구(35.1284, 129.0455) → 시드 마스터의 최근접 역은 좌천역(35.1343, 129.0544).
    // 출발지를 서울로 두면 "출발지 기준" 과 "도착역 기준" 이 서로 다른 장소를 첫 코스로 고르므로,
    // 첫 장소 하나만 봐도 어느 기준이 쓰였는지 드러난다.

    /** 도착역 코앞 — 도착역이 기준이면 여기서 시작한다. */
    private static final String NEAR_STATION = "near-station";
    /** 서울 쪽으로 크게 치우친 곳 — 출발지가 기준이면 여기서 시작한다. */
    private static final String NEAR_SEOUL = "near-seoul";

    private static final String ARRIVAL_STATION = "좌천";
    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;

    /** 두 기준이 서로 다른 답을 내도록 후보를 양극단에 둔다. 6곳 전부 선택된다(PACKED 2일 = 12곳 필요). */
    private static TourPoiResult spreadPois() {
        List<TourPoi> items = new ArrayList<>();
        items.add(poi(NEAR_STATION, 12, 35.135, 129.055)); // 좌천역에서 100m 남짓
        items.add(poi(NEAR_SEOUL, 12, 35.400, 129.000));   // 서울에서 가장 가깝다
        for (int i = 0; i < 4; i++) {
            items.add(poi("s" + i, 12, 35.20 + i * 0.03, 129.02 + i * 0.01));
        }
        items.add(poi("f0", 39, 35.12, 129.04));
        items.add(poi("f1", 39, 35.13, 129.05));
        items.add(poi("st0", 32, 35.11, 129.03));
        return new TourPoiResult(items, items.size());
    }

    private static String transitBody(String transport) {
        return """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "%s",
                  "originLat": %s, "originLng": %s, "travelDate": "2026-05-01" }"""
                .formatted(transport, SEOUL_LAT, SEOUL_LNG);
    }

    /**
     * 열차 동작을 정하고 <b>경로 캐시를 비운다.</b>
     *
     * <p>{@code TrainRouteService} 는 (출발역·도착역·날짜)를 6시간 캐시하는데 아래 시나리오가 전부 같은 조합이라,
     * 비우지 않으면 먼저 도는 테스트의 결과를 나머지가 그대로 물려받는다.
     */
    private void trainArrives(TrainAvailability availability) {
        trainInfoClient.respond(() -> availability);
        trainRouteService.evictCache();
    }

    private static TrainAvailability arrivingAt(int hour, int minute) {
        return new TrainAvailability.Available(TrainLeg.of("KTX",
                LocalDateTime.of(2026, 5, 1, 5, 0),
                LocalDateTime.of(2026, 5, 1, hour, minute)));
    }

    @Test
    void 대중교통_코스는_출발지가_아니라_내린_역_근처에서_시작한다() throws Exception {
        // 서울→부산 KTX 인데 집 좌표로 동선을 짜면 "부산 장소들 중 서울에서 가까운 곳" 부터 이어붙는다(#127).
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").value(NEAR_STATION))
                .andExpect(jsonPath("$.data.trainAccess.toStation").value(ARRIVAL_STATION));
    }

    @Test
    void 자차_코스는_출발지_기준_그대로다() throws Exception {
        // 회귀 방어 — 자차는 집에서 출발하므로 앵커가 바뀌면 안 된다. 같은 후보인데 첫 장소가 위 테스트와 달라야 한다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").value(NEAR_SEOUL))
                .andExpect(jsonPath("$.data.trainAccess").value(nullValue()));
    }

    @Test
    void 오후에_도착하면_1일차에_오전_일정을_넣지_않는다() throws Exception {
        // 오후 3시에 닿았는데 오전 일정을 주면 지킬 수 없는 코스가 된다. LNT 가 그만큼 과대계산된다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(15, 0));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[?(@.timeOfDay == 'MORNING')]").isEmpty())
                .andExpect(jsonPath("$.data.days[0].items[?(@.timeOfDay == 'LUNCH')]").isEmpty())
                .andExpect(jsonPath("$.data.days[0].items[0].timeOfDay").value("AFTERNOON"))
                // 둘째 날은 온전히 쓴다 — 첫날만 이동에 먹힌다
                .andExpect(jsonPath("$.data.days[1].items[0].timeOfDay").value("MORNING"));
    }

    @Test
    void 그날_운행이_없으면_1일차_일정을_깎지_않는다() throws Exception {
        // 도착 시각을 "모르는" 것이지 "늦은" 게 아니다. 모름을 늦음으로 단정하면 조회 실패가 조용히 코스를 깎는다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(new TrainAvailability.NoServiceOnDate());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].timeOfDay").value("MORNING"))
                // 시각은 몰라도 내리는 역은 안다 — 앵커는 그대로 도착역이다
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").value(NEAR_STATION))
                .andExpect(jsonPath("$.data.trainAccess.status").value("NO_SERVICE_ON_DATE"));
    }
}
