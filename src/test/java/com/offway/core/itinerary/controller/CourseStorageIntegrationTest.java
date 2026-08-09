package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.service.TrainRouteService;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// DB 격리: 롤백 대신 테스트마다 고유 게스트 ID 를 써서 "내 코스" 목록이 섞이지 않게 한다.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class CourseStorageIntegrationTest {

    private static final String URL = "/api/v1/courses";

    // 정선(16) 당일치기 · 유효한 코스(첫 슬롯 이동 0, 순서 연속)
    private static final String VALID_BODY = """
            { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0},
                {"order":2,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
              ]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

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
        TrainInfoClient stubTrainInfoClient() {
            return new StubTrainInfoClient();
        }

        @Bean
        @Primary
        KmaWeatherClient stubKmaWeatherClient() {
            return new StubKmaWeatherClient();
        }
    }

    @AfterEach
    void resetWeatherStub() {
        weatherClient.reset(); // 공유 컨텍스트 — 앞 테스트가 세팅한 예보가 다음 테스트로 새지 않게
    }

    /** 테스트마다 고유한 게스트 ID — 롤백 없이 "내 코스" 목록이 이전 실행과 섞이지 않게. */
    private static String uniqueGuest() {
        return "guest-" + UUID.randomUUID();
    }

    @Test
    void 코스를_저장하면_201로_courseId를_준다() throws Exception {
        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.courseId").isNumber())
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0));
    }

    @Test
    void 저장한_코스가_내_코스_목록과_상세에_나온다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].placeCount").value(2));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(2));
    }

    @Test
    void 남의_코스는_상세로_볼_수_없다_404() throws Exception {
        String owner = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 다른 게스트가 같은 courseId 를 조회 → 존재 여부를 흘리지 않도록 404
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 게스트_헤더가_없으면_400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 게스트_ID가_공백이면_400() throws Exception {
        // 빈 게스트 ID 를 허용하면 모든 요청이 한 묶음을 공유 → 도메인이 막고 400
        mockMvc.perform(post(URL).header("X-Guest-Id", "  ").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 슬롯_순서가_불연속이면_400_ITINERARY_002() throws Exception {
        String invalid = """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.5,"lng":128.6,"travelMinutes":0},
                    {"order":3,"timeOfDay":"LUNCH","kind":"FOOD","poiContentId":"c2","title":"맛집1","lat":37.51,"lng":128.61,"travelMinutes":15}
                  ]}
                ]}""";

        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 없는_코스_상세는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999999).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 코스를_삭제하면_목록과_상세에서_사라진다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다.
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 남의_코스는_삭제할_수_없고_그대로_남는다_404() throws Exception {
        String owner = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 403 이 아니라 404 — 403 이면 "그 ID 는 존재한다" 를 알려주는 셈이다.
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));

        // 거부로 끝나야 한다 — 주인 것이 지워졌으면 안 된다
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId));
    }

    @Test
    void 없는_코스_삭제는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", 999999).header("X-Guest-Id", uniqueGuest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_두_번_삭제하면_두_번째는_404() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk());
        // 더블클릭·재시도 — 이미 없으니 없는 코스와 같은 답이다
        mockMvc.perform(delete(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_동시에_삭제해도_500이_나지_않는다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        int threads = 2;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.List<Integer> statuses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        // 요청 단위 mock 인증 — @WithMockUser 는 현재 스레드 전용이라 이 요청엔 안 닿는다.
                        // 실제 계정을 쓰지 않으므로 운영 자격증명이 바뀌어도 이 테스트는 그대로다.
                        statuses.add(mockMvc.perform(delete(URL + "/{id}", courseId)
                                        .header("X-Guest-Id", guest)
                                        .with(user("test")))
                                .andReturn().getResponse().getStatus());
                    } catch (Exception e) {
                        statuses.add(-1);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            org.junit.jupiter.api.Assertions.assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        // 하나는 지우고 하나는 "없다" 여야 한다 — 순차 재삭제와 같은 계약이다. 500 이 섞이면 안 된다.
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of(200, 404), statuses.stream().sorted().toList(),
                "동시 삭제는 경합일 뿐 실패가 아니다. 실제=" + statuses);
    }

    // ── FE 가 준비를 끝냈는데 서버가 안 주던 것들 (#169 · #171) ───────────────────

    /** 날짜·이미지가 있는 1박2일 코스 — 날씨와 대표 이미지를 함께 확인한다. */
    private static String bodyWithDateAndImage(LocalDate travelDate) {
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "travelDate": "%s", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1",
                     "lat":37.50,"lng":128.60,"travelMinutes":0,"imageUrl":"http://img/cover.jpg"}
                  ]},
                  { "day": 2, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2",
                     "lat":37.51,"lng":128.61,"travelMinutes":0,"imageUrl":"http://img/second.jpg"}
                  ]}
                ]}""".formatted(travelDate);
    }

    private long save(String guest, String body) throws Exception {
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    @Test
    void 저장한_코스_상세에도_날씨가_실린다() throws Exception {
        // 생성 응답에는 날씨가 실리는데 저장 코스 조회에는 빠져 있어 화면이 비어 있었다(#169).
        LocalDate travelDate = LocalDate.now().plusDays(1);
        weatherClient.respondByDate(date ->
                Optional.of(new DailyWeather(date, 18, 27, SkyState.RAIN, 80)));
        String guest = uniqueGuest();
        long courseId = save(guest, bodyWithDateAndImage(travelDate));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather.sky").value("비"))
                .andExpect(jsonPath("$.data.days[0].weather.minTemp").value(18))
                .andExpect(jsonPath("$.data.days[0].weather.rainProbability").value(80))
                // Day 마다 따로 묻는다 — 첫날 것으로 전체를 대표하면 이튿날이 틀린다
                .andExpect(jsonPath("$.data.days[1].weather.date").value(travelDate.plusDays(1).toString()));
    }

    @Test
    void 날짜없이_저장한_코스는_날씨가_비어_있다() throws Exception {
        // 물어볼 기준이 없다. 지어내지 않고 비운다.
        weatherClient.respond(() -> Optional.of(new DailyWeather(LocalDate.now(), 18, 27, SkyState.CLEAR, 0)));
        String guest = uniqueGuest();
        long courseId = save(guest, VALID_BODY);

        // 키 자체가 빠진다 — "없는 값은 내려보내지 않는다"(응답 계약). null 로 내리지 않는다.
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist());
    }

    @Test
    void 목록에_지역명과_대표_이미지가_실린다() throws Exception {
        // 없어서 FE 가 코스마다 상세를 한 번씩 더 불렀다(#171).
        String guest = uniqueGuest();
        save(guest, bodyWithDateAndImage(LocalDate.now().plusDays(1)));

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("정선군"))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value("http://img/cover.jpg"));
    }

    @Test
    void 이미지가_없는_코스는_대표_이미지가_null이다() throws Exception {
        String guest = uniqueGuest();
        save(guest, VALID_BODY);

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("정선군"))
                .andExpect(jsonPath("$.data[0].coverImageUrl").doesNotExist());
    }

    @Test
    void 예보_범위_밖_여행은_날씨가_비어_있다() throws Exception {
        // 우리가 답할 수 있는 것은 D+10 까지다(단기 D+0~3 · 중기 D+4~10). 그 밖은 기상청도 예보를 내지 않는다.
        // 지어내지 않고 비운다 — 평년값으로 채우는 것은 후속(#133)이다.
        weatherClient.respondByDate(date -> Optional.empty());
        String guest = uniqueGuest();
        long courseId = save(guest, bodyWithDateAndImage(LocalDate.now().plusDays(30)));

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist())
                // 날씨가 없어도 코스는 그대로 나간다 — 부가 정보다
                .andExpect(jsonPath("$.data.days.length()").value(2));
    }

    /** 정선(16) 근처로 도착하는 열차 — 저장 코스에서 다시 계산될 때 쓰인다. */
    private void trainArrives() {
        trainInfoClient.respond(() -> new TrainAvailability.Available(TrainLeg.of("KTX",
                LocalDateTime.of(2026, 9, 11, 6, 0),
                LocalDateTime.of(2026, 9, 11, 8, 30))));
        trainRouteService.evictCache();
    }

    private static String transitBody(boolean withOrigin) {
        String origin = withOrigin ? "\"originLat\": 37.5547, \"originLng\": 126.9707," : "";
        return """
                { "regionId": 16, "density": "PACKED", "transport": "TRANSIT",
                  "travelDate": "2026-09-11", %s "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]}
                ]}""".formatted(origin);
    }

    @Test
    void 대중교통_코스는_저장_후에도_열차_접근이_나온다() throws Exception {
        // 생성 때 "청량리 → 정선" 을 보고 저장했는데 다시 열면 비어 있었다(#187).
        trainArrives();
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(transitBody(true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainAccess.toStation").value("정선"));
    }

    @Test
    void 출발지_없이_저장된_코스는_열차_접근이_비고_그게_오류가_아니다() throws Exception {
        // 이 필드가 생기기 전에 저장된 코스가 이 경우다. 근거가 없으니 지어내지 않는다.
        trainArrives();
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(transitBody(false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainAccess").doesNotExist())
                .andExpect(jsonPath("$.data.days.length()").value(1));
    }

    @Test
    void 자차_코스는_출발지가_있어도_열차_접근이_없다() throws Exception {
        // 자차는 열차 접근이 개념적으로 없다.
        trainArrives();
        String guest = uniqueGuest();
        String body = transitBody(true).replace("\"TRANSIT\"", "\"CAR\"");
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainAccess").doesNotExist());
    }
}
