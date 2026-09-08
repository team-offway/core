package com.offway.core.itinerary.controller;

import com.jayway.jsonpath.JsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.repository.UnroutableProbeJpaRepository;
import com.offway.core.transport.service.TrainRouteService;
import com.offway.core.transport.service.UnroutableCoordinateService;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.domain.FoodTaste;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.domain.RelatedAttraction;
import com.offway.core.trip.repository.HubAttractionRepository;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.repository.RelatedAttractionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseGenerateIntegrationTest {

    private static final String URL = "/api/v1/courses/generate";

    /** 이 클래스가 쓰는 지역. 순위 데이터를 붙일 때도 같은 곳이어야 한다. */
    private static final long REGION = 1L;

    /** 순위 데이터의 기준월 — 값 자체는 판정에 안 쓰이고, 없는 달이라 실제 적재와 안 겹친다. */
    private static final YearMonth RANK_BASE = YearMonth.of(2099, 1);

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

    @Autowired
    private UnroutableCoordinateService unroutableCoordinateService;

    @Autowired
    private UnroutableProbeJpaRepository unroutableProbeJpaRepository;

    @Autowired
    private RelatedAttractionRepository relatedAttractionRepository;

    @Autowired
    private HubAttractionRepository hubAttractionRepository;

    @Autowired
    private LicensedPlaceRepository licensedPlaceRepository;

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

    /**
     * 이 클래스는 트랜잭션 롤백이 없다(코스 생성이 쓰기 경로가 아니라 굳이 걸지 않았다). 차단 좌표만은
     * <b>DB 에 남는 쓰기</b>라, 지우지 않으면 다음 테스트의 후보에서 조용히 장소가 빠진다.
     */
    @AfterEach
    void clearUnroutableProbes() {
        unroutableProbeJpaRepository.deleteAll();
    }

    /**
     * 콘텐츠 타입에 <b>맞는 대분류</b>를 함께 준다.
     *
     * <p>예전에는 대분류를 {@code "NA"} 로 고정했다. 풀을 콘텐츠 타입으로 가르던 시절엔 문제가 없었지만,
     * 이제 대분류가 기준이라 <b>타입 39(음식점)인데 대분류가 자연</b>인 후보가 볼거리로 들어간다 —
     * 실제 응답에는 없는 조합이다(전수 6,821건 확인, 어긋난 건 0건).
     */
    /** 상호를 지정하는 후보 — 무슨 음식인지가 결과를 가르는 시나리오에 쓴다(#음식중복). */
    private static TourPoi namedPoi(String id, int contentTypeId, String title, double lat, double lng) {
        return new TourPoi(id, contentTypeId, lclsOf(contentTypeId), title, "부산 동구", lat, lng,
                "http://img/" + id + ".jpg", null, null);
    }

    /** 카페 후보 — 음식점 대분류 안의 카페 중분류({@code FD05})다(#522). */
    private static TourPoi cafePoi(String id, String title, double lat, double lng) {
        return TourPoi.builder()
                .contentId(id).contentTypeId(39).lclsSystm1("FD").lclsSystm2("FD05").title(title)
                .address("부산 동구").lat(lat).lng(lng).firstImage("http://img/" + id + ".jpg")
                .cat3("A05020900")
                .build();
    }

    /** 종류를 지정하는 볼거리 후보 — 하루에 같은 종류가 몰리는지 보는 시나리오에 쓴다(#522). */
    private static TourPoi sightPoi(String id, String title, String cat3, double lat, double lng) {
        return TourPoi.builder()
                .contentId(id).contentTypeId(12).lclsSystm1(lclsOf(12)).title(title)
                .address("부산 동구").lat(lat).lng(lng).firstImage("http://img/" + id + ".jpg")
                .cat3(cat3)
                .build();
    }

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
    void 화면이_그릴_재료를_함께_내린다_날짜_요일_거리_지역명() throws Exception {
        // day 1  5.1/금 · "관광명소 · 동구" · 장소 사이 거리 — 화면 명세(#141)가 요구하는 재료다.
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // Day 가 실제 날짜와 요일을 안다 — 프론트가 travelDate 에 더하지 않아도 된다
                .andExpect(jsonPath("$.data.days[0].date").value("2026-05-01"))
                .andExpect(jsonPath("$.data.days[0].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-05-02"))
                .andExpect(jsonPath("$.data.days[1].dayOfWeek").value("SATURDAY"))
                // 첫 장소는 이동 전이라 거리가 '없음' 이다. 0 으로 두면 화면이 "0m" 를 그린다
                .andExpect(jsonPath("$.data.days[0].items[0].distanceFromPrevMeters").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[1].distanceFromPrevMeters").isNumber())
                // 슬롯마다 "관광명소 · 동구" 로 붙일 짧은 지역명
                .andExpect(jsonPath("$.data.days[0].items[0].regionName").value("동구"));
    }

    @Test
    void 코스를_생성해_날짜별_타임라인과_혜택을_200으로_내린다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);

        // 부산 동구(시드 id 1)는 비수도권이라 숙박세일페스타 대상이지만 반값여행 16곳은 아니다.
        // 날짜도 그 정책 기간(6/11~8/31) 안이어야 혜택이 붙는다 — 5/1 은 발급 시작 전이라 빈다(#217).
        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-07-15" }""";

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
                // 비수도권 인구감소지역 + 발급 기간 내 → 숙박세일페스타 혜택
                .andExpect(jsonPath("$.data.benefits[0].text").value("숙박 할인"));
    }

    @Test
    void 날씨를_Day_마다_따로_내린다() throws Exception {
        // 2박3일이면 날마다 날씨가 다르다. 첫날 것 하나로 코스 전체를 대표하면 이튿날이 틀린다(#141).
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        LocalDate first = LocalDate.of(2026, 5, 1);
        weatherClient.respondByDate(date -> date.equals(first)
                ? Optional.of(new DailyWeather(date, 18, 27, SkyState.CLEAR, 20))
                : Optional.of(new DailyWeather(date, 12, 19, SkyState.CLOUDY, 80)));

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather.date").value("2026-05-01"))
                .andExpect(jsonPath("$.data.days[0].weather.minTemp").value(18))
                .andExpect(jsonPath("$.data.days[0].weather.sky").value("맑음"))
                .andExpect(jsonPath("$.data.days[0].weather.rainProbability").value(20))
                // 둘째 날은 다른 날씨여야 한다 — 같으면 첫날 것을 복사한 것이다
                .andExpect(jsonPath("$.data.days[1].weather.date").value("2026-05-02"))
                .andExpect(jsonPath("$.data.days[1].weather.minTemp").value(12))
                .andExpect(jsonPath("$.data.days[1].weather.sky").value("흐림"))
                .andExpect(jsonPath("$.data.days[1].weather.rainProbability").value(80));
    }

    @Test
    void 예보가_있는_Day_와_없는_Day_가_섞여도_각자_답한다() throws Exception {
        // D+11 이후처럼 예보가 없는 날이 뒤에 붙는다. 한 날이 비어도 나머지는 정상이어야 한다.
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        LocalDate first = LocalDate.of(2026, 5, 1);
        weatherClient.respondByDate(date -> date.equals(first)
                ? Optional.of(new DailyWeather(date, 18, 27, SkyState.CLEAR, 20))
                : Optional.empty());

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather.sky").value("맑음"))
                .andExpect(jsonPath("$.data.days[1].weather").doesNotExist());
    }

    @Test
    void 날씨_예보가_없어도_코스는_정상이다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        // 예보 범위 밖·조회 실패 → 빈 예보(stub 기본값). 날씨는 부가 정보라 코스는 그대로 200
        String body = """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(2))
                // 날씨는 부가 정보다 — 없어도 코스는 그대로 나간다
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist())
                .andExpect(jsonPath("$.data.days[1].weather").doesNotExist());
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
     * 이 지역에 안 닿는 수단.
     *
     * <p><b>울릉도에 버스 터미널은 없다.</b> 뭍까지 200㎞ 가 넘어 {@code BusTerminalResolver} 의 30㎞
     * 반경 안에 아무것도 안 잡힌다 — 여객선 말고는 닿는 수단이 없는 곳이다.
     *
     * <p>예전에는 부산 지역에 고속버스를 요청하는 것으로 이 시나리오를 만들었다. <b>그건 "안 닿는다"
     * 가 아니었다</b> — 고속 터미널이 반경 안에 있는데도 종류를 안 가린 최근접이 시외라 빈 값이 됐을
     * 뿐이다(#493). 그 자리를 고치자 이 테스트가 빨개져서, 전제가 틀렸던 것이 드러났다.
     */
    private static final String UNREACHABLE_MODE = "EXPRESS_BUS";

    /**
     * 수단을 고정해 보낸다(#453) — 카드에서 칩을 눌렀을 때의 요청이다.
     *
     * <p><b>지역 16(정선)을 쓴다.</b> 여기는 역보다 터미널이 지역에 가까워, 열차가 그날 안 다니면 자동
     * 선택이 버스로 넘어간다 — 고정이 실제로 갈리는 자리다. 기본 지역(1)은 자동도 열차라 고정을 지워도
     * 테스트가 통과해 버린다(부정 대조에서 확인했다).
     */
    private static String transitBodyWithMode(String transitMode) {
        return transitBodyWithMode(transitMode, SEOUL_LAT, SEOUL_LNG);
    }

    private static String transitBodyWithMode(String transitMode, double originLat, double originLng) {
        String mode = transitMode == null ? "" : "\"transitMode\": \"%s\",".formatted(transitMode);
        return """
                { "regionId": 16, "travelDays": 2, "density": "PACKED", "transport": "TRANSIT",
                  %s "originLat": %s, "originLng": %s, "travelDate": "2026-05-01" }"""
                .formatted(mode, originLat, originLng);
    }

    /**
     * 열차역이 하나도 없는 출발지 — 울릉도.
     *
     * <p>{@code TrainStationResolver} 가 50㎞ 안에서만 역을 찾는데 여기서 뭍까지는 200㎞ 가 넘어,
     * 열차 결과가 <b>도착 지점 없는 상태</b>({@code NO_STATION})가 된다. 스텁으로는 못 만드는 값이라
     * (스텁은 운행 편만 정한다) 좌표로 만든다.
     */
    private static final double NO_STATION_LAT = 37.4844;

    private static final double NO_STATION_LNG = 130.9058;

    /** 첫날 연차 단위를 실어 보낸다 — 출발 시각이 여기서 도출된다(#138). */
    private static String bodyWithStartDayLeave(String transport, String startDayLeave) {
        return """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "%s",
                  "originLat": %s, "originLng": %s, "travelDate": "2026-05-01",
                  "startDayLeave": "%s" }"""
                .formatted(transport, SEOUL_LAT, SEOUL_LNG, startDayLeave);
    }

    private List<String> sightsOf(String requestBody) throws Exception {
        String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.days[0].items[?(@.kind == 'SIGHT')].poiContentId");
    }

    @Test
    void 늦게_떠나면_첫날_볼거리가_줄어든다() throws Exception {
        // 대중교통 — 반반차(15시)는 종일(08시)보다 늦은 편을 타므로 첫날에 남는 시간대가 적다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainInfoClient.respond(() -> new TrainAvailability.Available(List.of(
                TrainLeg.of("KTX", LocalDateTime.of(2026, 5, 1, 9, 0), LocalDateTime.of(2026, 5, 1, 11, 0)),
                TrainLeg.of("KTX", LocalDateTime.of(2026, 5, 1, 16, 0), LocalDateTime.of(2026, 5, 1, 18, 0)))));

        trainRouteService.evictCache();
        List<String> fullDay = sightsOf(bodyWithStartDayLeave("TRANSIT", "FULL_DAY"));
        trainRouteService.evictCache();
        List<String> quarterDay = sightsOf(bodyWithStartDayLeave("TRANSIT", "QUARTER_DAY"));

        assertTrue(
                quarterDay.size() < fullDay.size(),
                "늦게 떠나면 첫날이 줄어야 한다 종일=%s 반반차=%s".formatted(fullDay, quarterDay));
    }

    @Test
    void 자차도_늦게_떠나면_첫날이_줄어든다() throws Exception {
        // 예전에는 자차를 하루 전부로 뒀다 — 15시에 나서도 오전 일정을 넣었다. 자차는 시간표가 없어
        // 출발 시각 + 이동시간이 곧 도착 시각이다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);

        List<String> fullDay = sightsOf(bodyWithStartDayLeave("CAR", "FULL_DAY"));
        List<String> quarterDay = sightsOf(bodyWithStartDayLeave("CAR", "QUARTER_DAY"));

        assertTrue(
                quarterDay.size() < fullDay.size(),
                "자차도 늦게 떠나면 첫날이 줄어야 한다 종일=%s 반반차=%s".formatted(fullDay, quarterDay));
    }

    @Test
    void 단위를_안_보내면_종일과_같다() throws Exception {
        // 안 보내던 클라이언트가 지금과 같은 결과를 받아야 한다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);

        assertEquals(sightsOf(bodyWithStartDayLeave("CAR", "FULL_DAY")), sightsOf(transitBody("CAR")));
    }

    @Test
    void 모르는_단위는_400이고_종일로_흘리지_않는다() throws Exception {
        // 오타를 종일로 흘리면 사용자는 반차를 골랐는데 코스가 아침 출발로 짜이고, 이유를 알 방법이 없다.
        //
        // 코드는 COMMON-400 이다. 요청 dto 가 enum 타입으로 받으므로 Jackson 이 먼저 막고, 그건 "본문을 읽을
        // 수 없다" 라서 프레임워크가 판정하는 자리다(exception-and-response 규약). 전용 에러코드를 두려
        // 했는데 그 파서를 아무도 부르지 않아 죽은 코드가 됐고, 번호는 append-only 라 넣지 않았다.
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithStartDayLeave("CAR", "HALFDAY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
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

    /**
     * 그 시각에 닿는 편 하나 — <b>출발은 도착 3시간 전</b>이다.
     *
     * <p>예전에는 05:00 출발로 고정했는데, 이제 종일 연차 기준(08:00) 이후 편만 고르므로(#138) 그 편은 걸러진다.
     * 걸러지면 "그날 열차 없음" 이 되어 첫날이 하루 전부로 열리고, 도착 시각을 검증하려던 테스트가 조용히
     * 반대 결과를 본다.
     */
    private static TrainAvailability arrivingAt(int hour, int minute) {
        LocalDateTime arriveAt = LocalDateTime.of(2026, 5, 1, hour, minute);
        return new TrainAvailability.Available(List.of(TrainLeg.of("KTX", arriveAt.minusHours(3), arriveAt)));
    }

    @Test
    void 대중교통_코스는_출발지가_아니라_내린_역_근처에서_시작한다() throws Exception {
        // 서울→부산 KTX 인데 집 좌표로 동선을 짜면 "부산 장소들 중 서울에서 가까운 곳" 부터 이어붙는다(#127).
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                // items[0] 은 도착 칸(역)이다(#415) — 첫 볼거리는 그 다음 칸이다
                .andExpect(jsonPath("$.data.days[0].items[1].poiContentId").value(NEAR_STATION))
                // 마스터 이름 그대로가 아니라 종류가 붙어 나간다(#529) — "좌천" 은 좌천 어디인지 말하지 않는다.
                .andExpect(jsonPath("$.data.trainAccess.toStation").value(ARRIVAL_STATION + "역"));
    }

    @Test
    void 대중교통_코스는_역에서_시작해_역에서_끝난다() throws Exception {
        // 그 두 칸이 없으면 "역에서 첫 장소까지 어떻게 가지" 와 "언제 역으로 나서지" 를 코스 밖에서 계산한다(#415).
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].kind").value("ARRIVAL"))
                .andExpect(jsonPath("$.data.days[0].items[0].categoryLabel").value("도착"))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value(ARRIVAL_STATION + "역"))
                .andExpect(jsonPath("$.data.days[0].items[0].order").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0))
                // 역·터미널은 장소 풀이 아니다 — 상세로 이어지지 않으므로 키를 지어내지 않는다
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> lastDay = JsonPath.read(body, "$.data.days[-1:].items[-1:]");
        Map<String, Object> tail = lastDay.getFirst();
        assertEquals("DEPARTURE", tail.get("kind"), "마지막 칸은 다시 타는 지점이어야 한다");
        assertEquals(ARRIVAL_STATION + "역", tail.get("title"));
        assertFalse(tail.containsKey("poiContentId"), "교통 거점 칸에는 장소 상세 키가 없다");
    }

    @Test
    void 역에서_첫_장소까지의_이동시간이_채워진다() throws Exception {
        // 예전에는 첫날 첫 칸이 곧바로 관광지라 이동시간이 0 이었다 — 역에서 거기까지 가는 시간이 없던 값이다(#415).
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int distance = JsonPath.read(body, "$.data.days[0].items[1].distanceFromPrevMeters");
        assertTrue(distance > 0, "역에서 첫 장소까지의 거리가 채워져야 한다");
    }

    @Test
    void 자차_코스에는_교통_거점_칸이_없다() throws Exception {
        // 내릴 역이 없다. 자차에도 접근 정보는 생기지만(#379) 그 도착 지점은 지역 중심이라 칸으로 세우면 거짓이 된다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);

        String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> hubs = JsonPath.read(body,
                "$.data.days[*].items[?(@.kind == 'ARRIVAL' || @.kind == 'DEPARTURE')].kind");
        assertTrue(hubs.isEmpty(), "자차 코스에는 도착·출발 칸이 없어야 한다: " + hubs);
    }

    @Test
    void 자차_코스는_출발지_기준_그대로다() throws Exception {
        // 회귀 방어 — 자차는 집에서 출발하므로 앵커가 바뀌면 안 된다. 같은 후보인데 첫 장소가 위 테스트와 달라야 한다.
        //
        // 첫날 items[0] 로 보지 않는다. 자차도 출발 시각 + 이동시간으로 첫날이 줄어들면서(#138) 서울→부산
        // 자차는 첫날에 볼거리가 안 들어간다 — 앵커는 그대로인데 관찰 지점이 사라졌다. 코스 전체에서 첫 볼거리를
        // 보면 일정이 어느 날로 밀리든 순서가 드러난다.
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);

        String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBody("CAR")))
                .andExpect(status().isOk())
                // 값이 없는 선택 필드는 응답에서 빠진다 — 자차 코스에는 열차 접근 정보가 없다.
                .andExpect(jsonPath("$.data.trainAccess").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> sights = JsonPath.read(body, "$.data.days[*].items[?(@.kind == 'SIGHT')].poiContentId");
        assertEquals(NEAR_SEOUL, sights.get(0), "자차는 집에서 출발하므로 서울에 가장 가까운 곳부터다");
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
                .andExpect(jsonPath("$.data.days[0].items[1].poiContentId").value(NEAR_STATION))
                .andExpect(jsonPath("$.data.trainAccess.status").value("NO_SERVICE_ON_DATE"));
    }

    /**
     * 첫날이 통째로 비면 그날은 코스에서 빠진다. 그때도 <b>둘째 날의 날짜가 둘째 날로</b> 나와야 한다.
     *
     * <p>예전에는 표시 번호로 날짜를 세서, 빠진 첫날만큼 날짜와 날씨가 하루씩 앞당겨졌다(#159).
     */
    @Test
    void 첫날이_비어도_남은_날의_날짜가_밀리지_않는다() throws Exception {
        // 밤 11시 도착 + 숙박 후보 없음 — 첫날에는 아무 슬롯도 잡히지 않는다.
        // (숙박은 시간대 판정을 타지 않으므로, 후보가 있으면 밤늦게라도 첫날이 채워진다)
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                items.add(poi("s" + i, 12, 35.20 + i * 0.03, 129.02 + i * 0.01));
            }
            items.add(poi("f0", 39, 35.12, 129.04));
            items.add(poi("f1", 39, 35.13, 129.05));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(23, 0));
        // 날짜별로 다른 예보를 준다 — 날씨까지 하루 앞당겨지는지 가리려면 값이 갈려야 한다.
        weatherClient.respondByDate(date -> Optional.of(new DailyWeather(
                date, date.getDayOfMonth(), date.getDayOfMonth() + 10, SkyState.CLEAR, 20)));

        String body = """
                { "regionId": 1, "travelDays": 2, "density": "RELAXED", "transport": "TRANSIT",
                  "originLat": 37.5665, "originLng": 126.9780, "travelDate": "2026-05-01" }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // 화면 탭은 1부터 이어진다
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                // 날짜는 달력을 따른다 — 5/1 이 아니라 5/2
                .andExpect(jsonPath("$.data.days[0].date").value("2026-05-02"))
                .andExpect(jsonPath("$.data.days[0].dayOfWeek").value("SATURDAY"))
                // 날씨도 그 날짜의 것이어야 한다. 표시 번호로 조회하면 5/1 예보가 붙는다
                .andExpect(jsonPath("$.data.days[0].weather.minTemp").value(2))
                // 요청한 출발일 자체는 그대로 보존된다
                .andExpect(jsonPath("$.data.travelDate").value("2026-05-01"));
    }

    @Test
    void 날짜가_바뀌는_구간의_거리와_시간을_함께_낸다() throws Exception {
        // 슬롯 사이 거리는 주면서 날짜가 바뀌는 구간만 비어 있었다(#188). 숙소에서 다음날 첫 장소가
        // 40km 떨어져 있어도 화면에 아무 표시가 없었다.
        tourApiClient.respond(CourseGenerateIntegrationTest::richPois);
        weatherClient.respondByDate(date -> Optional.empty());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(generateBodyOn(today())))
                .andExpect(status().isOk())
                // 첫날은 전날이 없다 — 키 자체가 나가지 않는다.
                .andExpect(jsonPath("$.data.days[0].distanceFromPrevDayMeters").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].travelMinutesFromPrevDay").doesNotExist())
                .andExpect(jsonPath("$.data.days[1].distanceFromPrevDayMeters").isNumber())
                .andExpect(jsonPath("$.data.days[1].travelMinutesFromPrevDay").isNumber())
                // 슬롯 규칙은 그대로다 — 하루 첫 슬롯의 앞 거리는 여전히 없다(FE 가 이걸로 하루 시작을 가른다).
                .andExpect(jsonPath("$.data.days[1].items[0].distanceFromPrevMeters").doesNotExist());
    }

    // ── 경로를 못 만드는 좌표 · 같은 좌표 중복 (#335) ────────────────────────

    /** PACKED 1일이면 필요 볼거리가 6곳이라, 네 곳짜리 풀은 <b>전부</b> 쓰인다 — 빠지면 그건 우리가 뺀 것이다. */
    private static final String ONE_DAY_BODY = """
            { "regionId": 1, "travelDays": 1, "density": "PACKED", "transport": "CAR",
              "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";

    private static TourPoiResult sights(TourPoi... items) {
        List<TourPoi> all = new ArrayList<>(List.of(items));
        all.add(poi("f0", 39, 35.11, 129.04));
        all.add(poi("f1", 39, 35.12, 129.05));
        return new TourPoiResult(all, all.size());
    }

    private static List<String> contentIdsOf(String response) {
        return JsonPath.read(response, "$.data.days[*].items[*].poiContentId");
    }

    private String generateOneDay() throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ONE_DAY_BODY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * 도로에 안 붙는 좌표는 코스에서 빠진다.
     *
     * <p>안 빼면 그 코스는 방문 순서와 이동시간이 조용히 <b>직선거리</b>로 떨어진다. 200 으로 정상 응답하므로
     * 사용자는 산을 직선으로 넘는 시간을 보면서도 틀린 값인지 알 방법이 없다.
     */
    @Test
    void 경로를_못_만드는_좌표는_코스에_안_들어간다() throws Exception {
        Coordinate unroutable = new Coordinate(35.13, 129.06);
        // 서로 다른 짝으로 두 번 — 그래야 "옆에 있었을 뿐인 좌표" 와 갈린다.
        unroutableCoordinateService.report(
                new Coordinate(35.90, 129.90), unroutable, UnroutableReason.NO_ROAD_LINK);
        unroutableCoordinateService.report(
                unroutable, new Coordinate(35.95, 129.95), UnroutableReason.NO_ROAD_LINK);
        tourApiClient.respond(() -> sights(
                poi("s0", 12, 35.10, 129.03),
                poi("s1", 12, 35.11, 129.04),
                poi("s2", 12, 35.12, 129.05),
                poi("s3", 12, unroutable.lat(), unroutable.lng())));

        List<String> contentIds = contentIdsOf(generateOneDay());

        assertFalse(contentIds.contains("s3"), "차단된 좌표의 장소가 실렸다: " + contentIds);
        assertTrue(contentIds.containsAll(List.of("s0", "s1", "s2")), "실제=" + contentIds);
    }

    /** 짝이 하나뿐이면 아직 차단하지 않는다 — 그 옆에 있었을 뿐인 멀쩡한 장소를 함께 빼면 안 된다. */
    @Test
    void 한_번만_걸린_좌표는_아직_코스에_남는다() throws Exception {
        Coordinate suspect = new Coordinate(35.13, 129.06);
        unroutableCoordinateService.report(
                new Coordinate(35.90, 129.90), suspect, UnroutableReason.NO_ROAD_LINK);
        tourApiClient.respond(() -> sights(
                poi("s0", 12, 35.10, 129.03),
                poi("s3", 12, suspect.lat(), suspect.lng())));

        assertTrue(contentIdsOf(generateOneDay()).contains("s3"));
    }

    /**
     * 같은 좌표의 장소가 여럿 뽑히면 화면에 <b>"이동 0분" 슬롯이 연속</b>으로 뜬다(운영 코스 67, 평창
     * 3일차에 넷이 그랬다). 지오코딩 오류가 아니라 실제 집합체라 데이터를 고칠 일이 아니다.
     */
    @Test
    void 같은_좌표의_볼거리는_한_코스에_하나만_들어간다() throws Exception {
        tourApiClient.respond(() -> sights(
                poi("s0", 12, 35.10, 129.03),
                poi("a1", 12, 37.6541478, 128.652815),
                poi("a2", 12, 37.6541478, 128.652815),
                poi("a3", 12, 37.6541478, 128.652815)));

        List<String> contentIds = contentIdsOf(generateOneDay());

        long alpensia = contentIds.stream().filter(id -> id.startsWith("a")).count();
        assertEquals(1, alpensia, "같은 좌표에서 하나만 남아야 한다. 실제=" + contentIds);
    }

    /** 지역 1(부산광역시 동구)의 시도. */
    private static final String REGION_SIDO = "부산광역시";

    private static LocalDate today() {
        return LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    }

    private static String generateBodyOn(LocalDate travelDate) {
        return """
                { "regionId": 1, "travelDays": 2, "density": "PACKED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "%s" }"""
                .formatted(travelDate);
    }

    /**
     * 수단을 고정하면 <b>자동 선택과 다른 답</b>을 준다(#453).
     *
     * <p>사용자가 카드에서 칩을 누르면 그 수단으로 다시 짠다. 시간표만 갈아끼우면 안 되는 이유는
     * <b>코스가 내린 곳에서 시작하기 때문</b>이다(#127) — 지역에 따라 역과 터미널이 수십 ㎞ 떨어져 있어
     * 도착 지점이 바뀌면 동선과 첫날 시각이 함께 바뀌어야 한다.
     *
     * <p><b>자동이 무엇을 고르는지 먼저 재고 비교한다.</b> 둘이 같은 지역에서 재면 고정 코드를 지워도
     * 통과한다 — 실제로 그렇게 써 놓고 부정 대조에서 발견했다. 그날 열차가 없으면 자동은 더 가까운
     * 터미널로 넘어가므로, 그 자리에서 열차를 고정해야 갈린다.
     */
    @Test
    void 수단을_고정하면_자동_선택과_다른_답을_준다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainInfoClient.respond(TrainAvailability.NoServiceOnDate::new);
        trainRouteService.evictCache();

        // **대표 값만 본다.** 응답 전체를 문자열로 훑으면 alternatives 안의 열차까지 걸려,
        // 자동이 버스를 골랐는데도 "열차를 골랐다" 로 읽힌다.
        String auto = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                                .content(transitBodyWithMode(null)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.transitAccess.mode");
        trainRouteService.evictCache();

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(transitBodyWithMode("TRAIN")))
                .andExpect(status().isOk())
                // 열차가 없어도 물어본 수단으로 답한다 — 없으면 없다고 말하는 것이 옳지,
                // 묻지도 않은 수단으로 바꿔치기할 일이 아니다
                .andExpect(jsonPath("$.data.transitAccess.mode").value("TRAIN"))
                .andExpect(jsonPath("$.data.transitAccess.status").value("NO_SERVICE_ON_DATE"))
                // 도착 칸도 그 수단의 지점이다 — 시간표만 바뀌는 것이 아니다
                .andExpect(jsonPath("$.data.days[0].items[0].categoryLabel").value("도착"));

        assertNotEquals("TRAIN", auto,
                "자동 선택도 열차를 골랐다 — 이 테스트는 고정 코드를 지워도 통과한다");
    }

    /**
     * 고속과 시외가 <b>한 자리를 두고 경쟁하지 않는다</b>(#493).
     *
     * <p>예전에는 지역마다 종류를 안 가린 최근접 터미널 하나만 풀었다. 종합터미널은 좌표가 같아 늘
     * 시외가 이겼고, 진 쪽은 <b>대안에도 안 남아</b> 89곳 중 68곳에서 고속버스가 통째로 사라졌다.
     *
     * <p>대표를 누가 가져가든 상관없다 — 그 규칙은 #463 이 정했고 여기서 안 건드린다. 여기서 보는 것은
     * <b>진 쪽이 화면에 남는가</b> 다.
     */
    @Test
    void 고속과_시외가_둘_다_있으면_대표로_진_쪽도_대안에_남는다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("TRANSIT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> modes = new ArrayList<>();
        modes.add(com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.mode"));
        modes.addAll(com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.alternatives[*].mode"));

        assertTrue(
                modes.contains("EXPRESS_BUS") && modes.contains("INTERCITY_BUS"),
                "부산에는 고속·시외 터미널이 둘 다 반경 안에 있다. 한쪽만 나오면 진 쪽이 사라진 것이다: " + modes);
    }

    /**
     * 고속버스로 고정하면 <b>고속으로 답한다</b>(#493 · #453).
     *
     * <p>예전에는 종류를 안 가린 최근접이 시외라 고정이 빈 값이 되고 그대로 자동 선택으로 떨어졌다 —
     * 사용자가 고른 수단이 조용히 무시됐다. 운영에서 거창·고성이 실제로 그랬다.
     */
    @Test
    void 고속버스로_고정하면_고속으로_답한다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBodyWithMode("EXPRESS_BUS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("EXPRESS_BUS"))
                // 출발 지점도 그 종류로 푼다 — 코드 공간이 갈려 있어 섞으면 조회 자체가 안 된다
                .andExpect(jsonPath("$.data.transitAccess.fromPlace").isNotEmpty());
    }

    /**
     * <b>하루에 해수욕장 두 곳은 안 나온다</b>(#522).
     *
     * <p>실제 코스에서 태안 2일차가 해수욕장 3곳이었다. 후보 구성이 그렇다 — 태안 관광지 78건 중
     * 해수욕장 30건·항구 21건이라 거리로만 고르면 상위가 바다로 채워진다.
     *
     * <p><b>후보를 전부 해수욕장으로 둔다.</b> 다른 종류를 섞으면 그것들이 먼저 뽑혀 규칙이 도는지
     * 안 도는지를 못 가른다 — 처음 쓴 픽스처가 그래서 규칙을 꺼도 통과했다.
     */
    @Test
    void 하루에_같은_종류_볼거리를_두_곳_넣지_않는다() throws Exception {
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            // 볼거리가 전부 해수욕장이다. 규칙이 없으면 하루가 통째로 해수욕장이 된다.
            for (int i = 0; i < 8; i++) {
                items.add(sightPoi("b" + i, "해수욕장" + i, "A01011200", 35.135 + i * 0.002, 129.055));
            }
            items.add(poi("f0", 39, 35.12, 129.04));
            items.add(poi("f1", 39, 35.13, 129.05));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // **모든 날을 본다.** 하루만 보면 그날에 안 걸렸을 뿐인지, 규칙이 도는지를 못 가른다.
        int days = ((List<?>) com.jayway.jsonpath.JsonPath.read(response, "$.data.days")).size();
        for (int day = 0; day < days; day++) {
            List<String> titles = com.jayway.jsonpath.JsonPath.read(
                    response, "$.data.days[" + day + "].items[?(@.kind == 'SIGHT')].title");
            assertTrue(titles.size() <= 1,
                    (day + 1) + "일차 볼거리가 " + titles.size() + "곳이다(전부 해수욕장): " + titles);
        }
    }

    /**
     * <b>카페는 끼니가 아니라 밥 다음이다</b>(#522).
     *
     * <p>TourAPI 음식점 대분류({@code FD})에 카페({@code FD05})가 섞여 있어 그대로 두면 카페가 점심
     * 자리에 뽑힌다 — 실제로 태안 1일차 점심이 `밀리앤코카페` 였다.
     */
    @Test
    void 카페는_끼니가_아니라_점심_뒤에_들어간다() throws Exception {
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
            }
            // 카페가 밥집보다 가깝다 — 규칙이 없으면 점심 자리를 카페가 차지한다.
            items.add(cafePoi("c0", "가까운카페", 35.100, 129.030));
            items.add(cafePoi("c1", "가까운카페2", 35.101, 129.031));
            items.add(poi("f0", 39, 35.20, 129.12));
            items.add(poi("f1", 39, 35.21, 129.13));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> meals = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'FOOD')].title");
        assertTrue(meals.stream().noneMatch(t -> t.contains("카페")),
                "카페가 끼니 자리에 들어갔다: " + meals);

        List<String> cafes = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'CAFE')].title");
        assertTrue(!cafes.isEmpty(), "밥 다음 카페가 한 칸도 안 들어갔다");
    }

    /**
     * <b>점심과 저녁이 같은 음식이면 안 된다.</b>
     *
     * <p>지역 특산이 곧 음식점 풀이라 거리만 보고 고르면 같은 것이 반복된다 — 실측(2026-09-08)에서
     * 태안 49건 중 꽃게가 9건, 평창 100건 중 메밀 11건, 횡성 23건 중 한우 4건이었고, 실제 코스에서
     * 횡성이 점심·저녁 모두 한우로 나왔다.
     *
     * <p>가장 가까운 두 곳이 같은 음식이면 <b>조금 더 먼 다른 음식</b>을 끌어온다.
     */
    @Test
    void 점심과_저녁에_같은_음식을_넣지_않는다() throws Exception {
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
            }
            // 가장 가까운 둘이 같은 음식(한우), 조금 더 먼 곳에 다른 음식이 있다.
            items.add(namedPoi("f0", 39, "횡성한우마을", 35.100, 129.030));
            items.add(namedPoi("f1", 39, "횡성순한우", 35.101, 129.031));
            items.add(namedPoi("f2", 39, "용둔막국수", 35.200, 129.120));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> foods = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'FOOD')].title");
        assertTrue(foods.size() >= 2, "끼니가 두 개는 나와야 비교가 성립한다: " + foods);
        // **포함 여부로는 안 갈린다.** 후보가 셋뿐이라 셋 다 뽑히면 무엇이 먼저인지가 유일한 차이다 —
        // 처음 쓴 단언이 그래서 회피를 꺼도 통과했다. 연달아 오는 두 끼니를 직접 본다.
        assertNotEquals(
                FoodTaste.of(foods.get(0)), FoodTaste.of(foods.get(1)),
                "점심과 저녁이 같은 음식이다: " + foods);
    }

    /**
     * 그 지역에 <b>안 닿는 수단</b>을 요청하면 서버가 고른 수단으로 돌아간다(#453).
     *
     * <p>억지로 세우면 도착 지점이 없는 코스가 된다 — 어디에 내리는지 모르는 채로 동선을 짜게 된다.
     */
    @Test
    void 안_닿는_수단을_요청하면_자동_선택으로_돌아간다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        String mode = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBodyWithModeFor(ULLEUNG, UNREACHABLE_MODE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals(UNREACHABLE_MODE, com.jayway.jsonpath.JsonPath.read(mode, "$.data.transitAccess.mode"),
                "이 지역에 안 닿는 수단인데 그대로 세웠습니다 — 도착 지점이 없는 코스가 됩니다");
    }

    /**
     * 열차를 고정했는데 <b>탈 역이 아예 없으면</b> 자동 선택으로 돌아간다(#453).
     *
     * <p>열차는 조회 결과가 어떻든 값이 나오므로 다른 수단처럼 "빈 값이면 자동" 이 성립하지 않는다.
     * 갈리는 것은 <b>도착 지점이 있느냐</b>다 — 없는 채로 세우면 어디에 내리는지 모르고 동선을 짜서
     * 코스가 출발지 기준으로 되돌아간다. 바로 아래 시나리오({@code 그날 운행이 없음})와 붙여서 읽는다:
     * 그쪽은 지점을 알아 열차로 남고, 이쪽은 몰라서 넘어간다.
     */
    @Test
    void 탈_역이_없는데_열차를_고정하면_자동_선택으로_돌아간다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBodyWithMode("TRAIN", NO_STATION_LAT, NO_STATION_LNG)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 역이 없다는 사실을 그대로 세우면 여기가 TRAIN 으로 남고 도착 지점이 빈다.
        assertNotEquals("TRAIN", com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.mode"),
                "탈 역이 없는데 열차로 세웠습니다 — 어디에 내리는지 모르는 코스가 됩니다");
        assertNotNull(com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.toPlace"),
                "자동으로 돌아갔는데도 도착 지점이 없습니다");
    }

    // ─── 탈 곳을 아는 수단을 대표로(#454) ───────────────────────────────────────

    /**
     * 지역 id 는 시드 순서에서 온다({@code V20260718200440__create_region_and_seed.sql}).
     * 이 파일이 이미 쓰고 있는 정선(16)과 같은 근거다.
     */
    private static final long WANDO = 57L;

    private static final long ULLEUNG = 74L;

    /** 지역과 수단을 함께 고정한다 — "이 지역에 이 수단이 닿는가" 를 묻는 자리다. */
    private static String transitBodyWithModeFor(long regionId, String transitMode) {
        return """
                { "regionId": %d, "travelDays": 2, "density": "PACKED", "transport": "TRANSIT",
                  "transitMode": "%s", "originLat": %s, "originLng": %s, "travelDate": "2026-05-01" }"""
                .formatted(regionId, transitMode, SEOUL_LAT, SEOUL_LNG);
    }

    private static String transitBodyFor(long regionId) {
        return """
                { "regionId": %d, "travelDays": 2, "density": "PACKED", "transport": "TRANSIT",
                  "originLat": %s, "originLng": %s, "travelDate": "2026-05-01" }"""
                .formatted(regionId, SEOUL_LAT, SEOUL_LNG);
    }

    /**
     * 도착 지점이 더 가깝다고 <b>탈 곳을 모르는 수단</b>을 앞세우지 않는다(#454).
     *
     * <p>완도는 항구(모황도)가 지역 중심에 가장 가까워 여객선이 대표였는데, <b>서울에 항구가 없어 출발
     * 지점이 늘 비었다</b> — 사용자에게는 "배로 가세요, 어디서 타는지는 모릅니다" 가 된다. 시외버스
     * 터미널이 '완도' 로 정확히 잡혀 있는데도 그랬다.
     *
     * <p>그날 열차가 없는 상황에서 잰다. 열차가 다니면 도착 지점 비교 자체를 안 하므로 갈리지 않는다.
     */
    @Test
    void 출발_지점을_모르는_수단은_대표가_되지_않는다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainInfoClient.respond(TrainAvailability.NoServiceOnDate::new);
        trainRouteService.evictCache();

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBodyFor(WANDO)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals("FERRY", com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.mode"),
                "서울에 항구가 없는데 여객선을 대표로 세웠습니다 — 어디서 타는지 말할 수 없습니다");
        assertNotNull(com.jayway.jsonpath.JsonPath.read(response, "$.data.transitAccess.fromPlace"),
                "대표로 세웠으면 어디서 타는지 말할 수 있어야 합니다");
    }

    /**
     * <b>다른 수단이 아예 없으면 배가 대표인 것이 맞다</b>(#454).
     *
     * <p>울릉군은 섬이라 역도 터미널도 없다. 그때는 출발 지점이 비는 것도 맞는 답이다 — 포항까지 육상으로
     * 간 뒤 배를 타는데, 그 환승을 잇는 것은 별개 작업이다. 여기서 여객선까지 빼면 도착 지점을 아는
     * 수단이 있는데도 "못 간다" 가 된다.
     */
    @Test
    void 다른_수단이_없으면_배가_대표인_것이_맞다() throws Exception {
        tourApiClient.respond(CourseGenerateIntegrationTest::spreadPois);
        trainInfoClient.respond(TrainAvailability.NoServiceOnDate::new);
        trainRouteService.evictCache();

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBodyFor(ULLEUNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("FERRY"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").isNotEmpty());
    }

    /**
     * <b>카페는 가까운 곳이 아니라 사람들이 실제로 들르는 곳이다</b>(#527).
     *
     * <p>카페 후보의 대부분은 사진 없는 인허가 장소다 — 실측(2026-09-08)에서 지역당 TourAPI 카페가
     * 0~10곳인데 인허가 카페는 상한인 100곳이 들어온다. 거리로 고르면 수로 밀려 그쪽이 이기는데,
     * 그 100곳은 <b>이름 가나다순으로 잘린 것</b>이라 아무 근거가 없다.
     *
     * <p>연관 관광지의 {@code 음식} 분류에 카페가 들어 있다 — 89곳 중 85곳에 있고 지역당 평균 4곳이다.
     * 그 순위를 거리보다 먼저 본다.
     */
    @Test
    @Transactional
    void 카페는_가까운_곳이_아니라_함께_가는_곳을_고른다() throws Exception {
        // 볼거리 한복판에서 떨어뜨리되 **권역(30㎞) 안**에 둔다 — 밖에 두면 상한에 걸려
        // 무엇이 판정했는지가 흐려진다. 약 17㎞ 로, 코앞 카페들에는 거리로 절대 못 이긴다.
        String 이름 = "멀리있는함께가는카페";
        licensedPlaceRepository.saveAll(List.of(LicensedPlace.builder()
                .regionId(REGION).kind(PlaceKind.CAFE).category(PlaceCategory.COFFEE)
                .name(이름).address("부산광역시 동구 어딘가 1")
                .lat(35.25).lng(129.03)
                .build()));
        LicensedPlace 함께가는카페 = licensedPlaceRepository.findAllInRegion(REGION).stream()
                .filter(place -> 이름.equals(place.getName()))
                .findFirst().orElse(null);
        assertNotNull(함께가는카페, "인허가 카페를 못 만들었다");

        relatedAttractionRepository.replaceRegion(REGION, RANK_BASE, List.of(RelatedAttraction.builder()
                .regionId(REGION).baseMonth(RANK_BASE)
                .hubCode("HUB-1").hubName("어느 중심")
                .relatedCode("RLT-" + 함께가는카페.getId()).relatedName(함께가는카페.getName())
                .relatedRank(1).categoryLarge("음식")
                .licensedPlaceId(함께가는카페.getId())
                .lat(함께가는카페.getLat()).lng(함께가는카페.getLng())
                .build()));

        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.01, 129.03 + i * 0.01));
            }
            // 사진까지 있고 볼거리 한복판에 있는 카페 둘 — 거리로 고르면 반드시 이쪽이 이긴다.
            items.add(cafePoi("c0", "코앞카페", 35.100, 129.030));
            items.add(cafePoi("c1", "코앞카페2", 35.101, 129.031));
            items.add(poi("f0", 39, 35.20, 129.12));
            items.add(poi("f1", 39, 35.21, 129.13));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> cafes = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'CAFE')].title");
        assertFalse(cafes.isEmpty(), "카페가 한 칸도 안 들어갔다");
        assertTrue(cafes.contains(함께가는카페.getName()),
                "함께 가는 카페 대신 가까운 카페를 골랐다: " + cafes);
    }

    /**
     * <b>볼거리는 좌표만 보지 않는다</b>(#527).
     *
     * <p>연관 관광지 순서는 인허가 볼거리({@code LIC-})에만 걸리는데, 인허가 볼거리는 후보가 18곳에
     * 못 미칠 때만 보충된다. 실측에서 TourAPI 볼거리가 양양 71·보령 68·태안 63·완도 35 라 <b>보충이
     * 안 돌고</b>, 그래서 연관 경로가 한 건도 안 걸려 기하학만 돌고 있었다.
     *
     * <p>중심관광지(지역당 30곳·89곳 전부)로 그 자리를 메운다. 여기서는 <b>가장 먼 볼거리</b>에 1·2위를
     * 매겨, 거리로는 절대 안 뽑힐 곳이 뽑히는지를 본다.
     */
    @Test
    @Transactional
    void 볼거리는_거리보다_인기순을_먼저_본다() throws Exception {
        // 후보를 필요분(빡빡 2일 = 12곳)보다 넉넉히 둔다. 후보가 얇으면 전부 뽑혀 규칙이 도는지 안 도는지
        // 를 못 가른다 — #522 에서 실제로 그렇게 가짜 통과한 적이 있다.
        int pool = 20;
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < pool; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.02, 129.03 + i * 0.02));
            }
            items.add(poi("f0", 39, 35.11, 129.04));
            items.add(poi("f1", 39, 35.12, 129.05));
            items.add(poi("st0", 32, 35.10, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        // 가장 먼 두 곳에 1·2위. 좌표는 일부러 엉뚱한 곳에 둬서 **이름으로만** 걸리게 한다.
        hubAttractionRepository.replaceRegion(REGION, List.of(
                중심("장소s" + (pool - 1), 1),
                중심("장소s" + (pool - 2), 2)));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> sights = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'SIGHT')].title");
        assertTrue(sights.contains("장소s" + (pool - 1)) && sights.contains("장소s" + (pool - 2)),
                "인기 1·2위가 코스에 없다 — 거리로만 골랐다는 뜻이다: " + sights);
    }

    private static HubAttraction 중심(String name, int rank) {
        return HubAttraction.builder()
                .regionId(REGION).baseMonth(RANK_BASE)
                .hubRank(rank).hubCode("HUB-" + rank)
                .name(name).categoryLarge("관광지")
                // 이름으로 걸리는지를 보려고 좌표는 멀리 둔다 — 300m 규칙이 대신 맞춰버리면 판정이 흐려진다.
                .lat(33.5).lng(126.5)
                .build();
    }

    /**
     * <b>고른 카페를 그날 동선에 맞춰 놓는다</b>(#527) — "여기 들른 김에 저기도".
     *
     * <p>예전에는 배열 순서대로 꽂았다({@code cafes.get(ci++)}). 그러면 2일차가 북쪽인데 남쪽 카페를
     * 받는 일이 생긴다 — 순위로 잘 골라 놔도 그날 동선과 무관하면 못 간다.
     *
     * <p>여기서는 <b>순위와 거리를 어긋나게</b> 둔다. 1순위 카페를 2일차 쪽에, 2순위를 1일차 쪽에 두고
     * 각 날이 자기 쪽 카페를 받는지 본다. 순서대로 꽂으면 정확히 반대가 나온다.
     */
    @Test
    @Transactional
    void 카페는_그날_볼거리에_가까운_것을_받는다() throws Exception {
        LicensedPlace 남쪽 = 인허가카페("남쪽카페", 35.10, 129.03);
        LicensedPlace 북쪽 = 인허가카페("북쪽카페", 35.80, 129.03);
        // 1순위는 북쪽, 2순위는 남쪽 — 순서대로 꽂으면 1일차가 북쪽 카페를 받는다.
        relatedAttractionRepository.replaceRegion(REGION, RANK_BASE, List.of(
                연관음식(북쪽, 1), 연관음식(남쪽, 2)));

        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            // 두 무리를 약 78㎞ 떼어 놓고 **무리끼리 붙여서** 넣는다. 번갈아 넣으면 하루에 섞인다 —
            // 테스트에서는 구간 이동시간이 동률이라 동선 정렬이 입력 순서를 그대로 남기기 때문이다.
            for (int i = 0; i < 3; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.002, 129.03));
            }
            for (int i = 0; i < 3; i++) {
                items.add(poi("n" + i, 12, 35.80 + i * 0.002, 129.03));
            }
            // 이틀 모두 점심이 있어야 카페 자리가 이틀치 생긴다.
            items.add(poi("f0", 39, 35.10, 129.04));
            items.add(poi("f1", 39, 35.11, 129.04));
            items.add(poi("f2", 39, 35.80, 129.04));
            items.add(poi("f3", 39, 35.81, 129.04));
            items.add(poi("st0", 32, 35.10, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        // 널널(하루 3곳)이라 6곳이 이틀에 3+3 으로 갈린다 — 두 무리가 하루씩 맡는다.
        // **출발지를 지역 근처에 둔다.** 서울에서 오면 첫날 도착이 늦어 볼거리가 한 곳만 들어가고,
        // 그러면 이틀이 무리별로 안 갈려 무엇이 판정했는지가 흐려진다.
        String body = """
                { "regionId": 1, "travelDays": 2, "density": "RELAXED", "transport": "CAR",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";
        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int days = com.jayway.jsonpath.JsonPath.read(response, "$.data.days.length()");
        int checked = 0;
        for (int day = 0; day < days; day++) {
            List<String> cafes = com.jayway.jsonpath.JsonPath.read(
                    response, "$.data.days[" + day + "].items[?(@.kind == 'CAFE')].title");
            List<Double> lats = com.jayway.jsonpath.JsonPath.read(
                    response, "$.data.days[" + day + "].items[?(@.kind == 'SIGHT')].lat");
            if (cafes.isEmpty() || lats.isEmpty()) {
                continue;
            }
            // 어느 날이 어느 무리인지는 동선 정렬이 정하므로 가정하지 않는다. **규칙 그대로** 본다 —
            // 그날 볼거리 중심에서 더 가까운 카페를 받았는가.
            double center = lats.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            assertEquals(Math.abs(center - 35.10) <= Math.abs(center - 35.80) ? "남쪽카페" : "북쪽카페",
                    cafes.getFirst(),
                    "그날 볼거리에서 먼 카페를 받았다 — 순서대로 꽂았다는 뜻이다 (day " + day
                            + ", 중심 위도 " + center + "): " + cafes);
            checked++;
        }
        assertEquals(2, checked, "이틀 모두 카페가 있어야 이 시나리오가 성립한다");
    }

    /**
     * <b>순위를 따르되 권역 밖은 건너뛴다</b>(#527).
     *
     * <p>순위는 "그 관광지 가는 사람이 들르는 곳" 이지 "우리 코스에서 갈 만한 곳" 이 아니다. 섬이 흩어진
     * 지역에서는 1순위가 볼거리 중심에서 수십 ㎞ 밖에 있다 — 실측에서 카페 3곳 합산 이동이 최대 226㎞ 였다.
     *
     * <p>상한(30㎞)의 근거는 {@link CafePreference} 가 소유한다. 여기서는 <b>상한 밖 1순위</b>를 두고
     * 그것이 뽑히지 않는지만 본다.
     */
    @Test
    @Transactional
    void 순위가_높아도_권역_밖_카페는_건너뛴다() throws Exception {
        // 볼거리는 35.10 언저리다. 위도 0.5도 = 약 55㎞ — 30㎞ 상한 밖이다.
        LicensedPlace 너무먼카페 = 인허가카페("너무먼1순위카페", 35.62, 129.03);
        relatedAttractionRepository.replaceRegion(REGION, RANK_BASE, List.of(연관음식(너무먼카페, 1)));

        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                items.add(poi("s" + i, 12, 35.10 + i * 0.002, 129.03 + i * 0.002));
            }
            items.add(cafePoi("c0", "가까운사진카페", 35.101, 129.031));
            items.add(poi("f0", 39, 35.11, 129.04));
            items.add(poi("f1", 39, 35.12, 129.05));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(transitBody("CAR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> cafes = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[*].items[?(@.kind == 'CAFE')].title");
        assertFalse(cafes.contains("너무먼1순위카페"),
                "권역 밖 1순위가 뽑혔다 — 상한이 안 걸렸다: " + cafes);
        assertTrue(cafes.contains("가까운사진카페"),
                "권역 밖을 건너뛰었으면 사진 있는 카페가 와야 한다: " + cafes);
    }

    /** 그 지역 인허가 카페 하나 — 트랜잭션 롤백이라 이 테스트 밖으로 안 나간다. */
    private LicensedPlace 인허가카페(String name, double lat, double lng) {
        licensedPlaceRepository.saveAll(List.of(LicensedPlace.builder()
                .regionId(REGION).kind(PlaceKind.CAFE).category(PlaceCategory.COFFEE)
                .name(name).address("부산광역시 동구 어딘가 1")
                .lat(lat).lng(lng)
                .build()));
        return licensedPlaceRepository.findAllInRegion(REGION).stream()
                .filter(place -> name.equals(place.getName()))
                .findFirst().orElseThrow();
    }

    private static RelatedAttraction 연관음식(LicensedPlace place, int rank) {
        return RelatedAttraction.builder()
                .regionId(REGION).baseMonth(RANK_BASE)
                .hubCode("HUB-" + rank).hubName("어느 중심 " + rank)
                .relatedCode("RLT-" + place.getId()).relatedName(place.getName())
                .relatedRank(rank).categoryLarge("음식")
                .licensedPlaceId(place.getId())
                .lat(place.getLat()).lng(place.getLng())
                .build();
    }

    /**
     * <b>대중교통 코스도 순서를 다듬는다</b>(#531).
     *
     * <p>예전에는 다듬는 단계가 자차에만 있었다(TMAP 경유지 최적화). 대중교통은 그리디 최근접 그대로라,
     * 마지막에 멀리 튀는 구간이 남았다. TMAP 은 일일 50회라 여기까지 태울 수도 없다.
     *
     * <p>한 줄 위에 볼거리를 늘어놓고 <b>가운데를 건너뛰게</b> 후보를 준다. 다듬기가 없으면 그 순서가
     * 그대로 남고, 있으면 왼쪽에서 오른쪽으로 정리된다.
     */
    @Test
    void 대중교통_코스도_들르는_순서를_다듬는다() throws Exception {
        tourApiClient.respond(() -> {
            List<TourPoi> items = new ArrayList<>();
            // 위도만 올라가는 한 줄. 최적 순서는 가까운 쪽부터 차례로다.
            for (int i = 0; i < 6; i++) {
                items.add(namedPoi("s" + i, 12, "지점" + i, 35.10 + i * 0.05, 129.03));
            }
            items.add(poi("f0", 39, 35.11, 129.04));
            items.add(poi("f1", 39, 35.12, 129.05));
            items.add(poi("st0", 32, 35.11, 129.03));
            return new TourPoiResult(items, items.size());
        });
        trainArrives(arrivingAt(8, 30));

        String body = """
                { "regionId": 1, "travelDays": 1, "density": "PACKED", "transport": "TRANSIT",
                  "originLat": 35.10, "originLng": 129.03, "travelDate": "2026-05-01" }""";
        String response = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Double> lats = com.jayway.jsonpath.JsonPath.read(
                response, "$.data.days[0].items[?(@.kind == 'SIGHT')].lat");
        assertTrue(lats.size() >= 3, "볼거리가 너무 적어 순서를 볼 수 없다: " + lats);

        // 한 줄 위에서 다듬어진 순서는 단조롭다 — 갔다가 되돌아오는 구간이 없다.
        double total = 0;
        for (int i = 0; i + 1 < lats.size(); i++) {
            total += Math.abs(lats.get(i + 1) - lats.get(i));
        }
        double span = lats.stream().mapToDouble(Double::doubleValue).max().orElseThrow()
                - lats.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        assertEquals(span, total, 1e-9,
                "왔다 갔다 하는 구간이 남았다 — 순서를 안 다듬었다는 뜻이다: " + lats);
    }
}
