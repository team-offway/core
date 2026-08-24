package com.offway.core.itinerary.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.policy.domain.Policy;
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
import java.util.List;
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
    /** {@code VALID_BODY} 가 쓰는 지역 — 혜택 대조에서 같은 지역을 봐야 한다. */
    private static final long REGION_ID = 16L;

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
    private com.offway.core.policy.repository.PolicyRepository policyRepository;

    @Autowired
    private com.offway.core.policy.service.PolicyService policyService;

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
    void 목록에_지역명이_실린다() throws Exception {
        // 없어서 FE 가 코스마다 상세를 한 번씩 더 불렀다(#171).
        String guest = uniqueGuest();
        save(guest, bodyWithDateAndImage(LocalDate.now().plusDays(1)));

        mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("정선군"));
    }

    /**
     * 카드 사진은 <b>코스가 아니라 지역</b>의 것이다(#313).
     *
     * <p>예전에는 코스 첫 장소의 사진이었다. 같은 공주시 코스인데 하나는 석탑, 하나는 소나무숲으로 떴다 —
     * 코스를 다시 뽑으면 첫 장소가 바뀌기 때문이다. 카드가 대표하는 것은 그 코스가 아니라 "내가 담은
     * 공주시 여행" 이라, 지역이 같으면 사진도 같아야 목록에서 지역이 눈에 들어온다.
     *
     * <p>사진을 실은 코스와 안 실은 코스를 <b>같은 지역으로</b> 담아, 첫 장소가 달라도 카드 사진이 갈리지
     * 않는 것을 본다. 그것이 이 이슈가 고친 바로 그 증상이다.
     */
    @Test
    void 같은_지역_코스는_첫_장소가_달라도_같은_사진이다() throws Exception {
        String guest = uniqueGuest();
        save(guest, bodyWithDateAndImage(LocalDate.now().plusDays(1)));
        save(guest, VALID_BODY);

        String body = mockMvc.perform(get(URL).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        List<String> covers = JsonPath.read(body, "$.data[*].coverImageUrl");
        // 지역 대표 사진을 못 고른 지역이면 둘 다 없다 — 그때도 "갈리지 않는다" 는 성질은 지켜진다.
        assertEquals(1, covers.stream().distinct().count(),
                "같은 지역 코스의 카드 사진이 갈렸다: " + covers);
        // 첫 장소 사진(http://img/cover.jpg)이 그대로 실리면 옛 동작으로 되돌아간 것이다.
        assertFalse(covers.contains("http://img/cover.jpg"),
                "카드 사진이 다시 코스 첫 장소의 것이 됐다");
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
        trainInfoClient.respond(() -> new TrainAvailability.Available(List.of(TrainLeg.of("KTX",
                LocalDateTime.of(2026, 9, 11, 6, 0),
                LocalDateTime.of(2026, 9, 11, 8, 30)))));
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
    void 출발지_좌표가_한쪽만_오면_400이다() throws Exception {
        // 조용히 버리면 클라이언트는 출발지를 보냈다고 여기는데 저장 코스에서 열차 접근이 빈다.
        // Day 날짜(#180)에서 시작일 없이 날짜만 온 요청을 거절한 것과 같은 판단이다.
        String latOnly = transitBody(false).replace("\"travelDate\": \"2026-09-11\",",
                "\"travelDate\": \"2026-09-11\", \"originLat\": 37.5547,");

        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest())
                        .contentType(MediaType.APPLICATION_JSON).content(latOnly))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
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

    /**
     * 첫날이 이동뿐이라 일정에서 빠진 2박3일(#159) — 생성 응답은 Day 1 이 9/12, Day 2 가 9/13 이다.
     *
     * @param dayDates 각 Day 에 실어 보낼 date. null 이면 필드를 빼서 종전 동작을 재현한다
     */
    private static String firstDayEmptyBody(String... dayDates) {
        String day1 = dayDates[0] == null ? "" : "\"date\":\"" + dayDates[0] + "\",";
        String day2 = dayDates[1] == null ? "" : "\"date\":\"" + dayDates[1] + "\",";
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR",
                  "travelDate": "2026-09-11", "travelDays": 3, "days": [
                  { "day": 1, %s "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]},
                  { "day": 2, %s "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
                  ]}
                ]}""".formatted(day1, day2);
    }

    @Test
    void 날짜를_보내면_첫날이_빠진_코스도_생성_때와_같은_날짜로_저장된다() throws Exception {
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstDayEmptyBody("2026-09-12", "2026-09-13")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        // 예전에는 며칠째를 그대로 달력 위치로 봐서 9/11·9/12 로 하루씩 당겨졌다.
        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-12"))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-09-13"));
    }

    @Test
    void 날짜를_안_보내면_종전대로_며칠째를_달력_위치로_본다() throws Exception {
        // 이 필드가 생기기 전 연동이 그대로 도는지 — 하위 호환.
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstDayEmptyBody(null, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-11"))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-09-12"));
    }

    @Test
    void 여행_시작일보다_앞선_날짜는_400이다() throws Exception {
        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstDayEmptyBody("2026-09-10", "2026-09-13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 여행_시작일_없이_Day_날짜만_보내면_400이다() throws Exception {
        // 기준점이 없으면 날짜를 오프셋으로 옮길 수 없다. 조용히 무시하면 지정한 날짜와 다른 값이 저장된다.
        String body = """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
                  { "day": 1, "date": "2026-09-12", "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]}
                ]}""";

        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 여행_기간을_넘는_날짜는_400이다() throws Exception {
        // 2026-09-11 시작 3일이면 9/13 이 마지막이다. 9/14 는 종료일 뒤라 앞뒤가 안 맞는다(#164).
        mockMvc.perform(post(URL).header("X-Guest-Id", uniqueGuest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstDayEmptyBody("2026-09-12", "2026-09-14")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    /**
     * 저장 코스의 혜택이 <b>여행일</b> 기준으로 매칭되는가(#213).
     *
     * <p>예전에는 이 자리만 오늘을 넘겨, 생성 응답과 상세 조회의 혜택이 어긋났다. 정책에는 유효기간이 있어
     * 기준일이 곧 결과를 가른다 — 여행 전에 끝나는 혜택이 보이거나, 여행 기간에 시작하는 혜택이 안 보였다.
     *
     * <p><b>날짜를 시드에서 읽어 정한다.</b> 처음에는 2030-01-01 을 박아 뒀는데, "시드 정책이 2026년에
     * 끝난다" 는 사실에 기대는 값이라 시간이 지나면 옛 구현도 통과해 조용히 무의미해진다. 실제로 유효기간이
     * 있는 정책 하나를 골라, 그 기간 <b>안</b>과 <b>한참 뒤</b> 두 날짜로 코스를 만든다.
     */
    @Test
    void 저장_코스의_혜택은_오늘이_아니라_여행일로_매칭된다() throws Exception {
        // 시드에서 **이 지역에 실제로 혜택이 붙는** 날짜를 찾는다. 아무 정책의 기간이나 쓰면 그 정책이
        // 이 지역 대상이 아닐 수 있다(정책은 지역 태그로도 걸러진다).
        List<LocalDate> periodEnds = policyRepository.findAllVerified().stream()
                .map(Policy::getPeriodEnd)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        LocalDate inPeriod = periodEnds.stream()
                .filter(date -> !policyService.matchForRegion(REGION_ID, date).isEmpty())
                .findFirst()
                .orElse(null);
        assumeTrue(inPeriod != null, "이 지역에 기간이 있는 혜택이 시드에 없으면 대조가 성립하지 않는다");
        LocalDate afterPeriod = periodEnds.getLast().plusYears(5);

        int inPeriodBenefits = benefitCountOf(inPeriod);
        int afterPeriodBenefits = benefitCountOf(afterPeriod);

        assertTrue(inPeriodBenefits > 0, "기간 안 여행이면 혜택이 실려야 한다: " + inPeriod);
        assertEquals(0, afterPeriodBenefits, "기간 밖 여행이면 혜택이 없어야 한다: " + afterPeriod);
    }

    /**
     * 그 여행일로 코스를 저장하고, <b>저장 응답과 상세 조회의 혜택 수가 같은지</b> 확인한 뒤 그 수를 준다.
     *
     * <p>둘을 함께 보는 이유: 상세만 검증하면 생성·저장 경로의 매칭이 틀려도 통과한다. 이 이슈가 고친 것이
     * "같은 코스인데 경로마다 답이 다르다" 라, 일치 자체가 검증 대상이다.
     */
    private int benefitCountOf(LocalDate travelDate) throws Exception {
        String guest = uniqueGuest();
        String body = VALID_BODY.replace(
                "{ \"regionId\": 16,", "{ \"travelDate\": \"" + travelDate + "\", \"regionId\": 16,");

        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");
        int onSave = ((List<?>) JsonPath.read(saved, "$.data.benefits")).size();

        String detail = mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.travelDate").value(travelDate.toString()))
                .andReturn().getResponse().getContentAsString();
        int onDetail = ((List<?>) JsonPath.read(detail, "$.data.benefits")).size();

        assertEquals(onSave, onDetail, "저장 응답과 상세 조회의 혜택이 같아야 한다 travelDate=" + travelDate);
        return onDetail;
    }

    /** 이틀짜리 대중교통 코스 — 첫날을 걷어내도 코스가 남아야 판정을 볼 수 있다. */
    private static String transitTwoDayBody(String travelDate) {
        return """
                { "regionId": 16, "density": "PACKED", "transport": "TRANSIT",
                  "travelDate": "%s", "originLat": 37.5547, "originLng": 126.9707, "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]},
                  { "day": 2, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c2","title":"장소2","lat":37.51,"lng":128.61,"travelMinutes":0}
                  ]}
                ]}""".formatted(travelDate);
    }

    private void trainArrivesAt(LocalDateTime arriveAt) {
        trainInfoClient.respond(() -> new TrainAvailability.Available(
                List.of(TrainLeg.of("KTX", arriveAt.minusHours(3), arriveAt))));
        trainRouteService.evictCache();
    }

    /**
     * 날짜를 옮겨 도착이 자정을 넘기면 <b>갈 수 없게 된 첫날 일정을 걷어낸다</b>(#214).
     *
     * <p>예전에는 열차 도착만 새 날짜로 다시 조회하고 슬롯은 저장된 그대로 뒀다. 그래서 도착 전 시간에
     * 일정이 잡힌 코스가 남았다 — 화면상 멀쩡한데 실제로는 갈 수 없다.
     */
    @Test
    void 날짜를_옮겨_도착이_자정을_넘기면_첫날_일정을_걷어낸다() throws Exception {
        weatherClient.respondByDate(date -> Optional.empty());
        trainArrivesAt(LocalDateTime.of(2026, 9, 11, 8, 30)); // 당일 오전 도착 — 첫날 일정 정상
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(transitTwoDayBody("2026-09-11")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        trainArrivesAt(LocalDateTime.of(2026, 9, 21, 2, 0)); // 옮긴 날짜의 막차 — 자정을 넘겨 닿는다

        mockMvc.perform(patch(URL + "/{id}", courseId).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstDayChange").value("TRIMMED"))
                // 첫날이 통째로 빠져 하루만 남고, 표시 번호는 1부터 다시 붙는다
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].day").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소2"));
    }

    @Test
    void 걷어낸_결과가_다시_열어도_남아_있다() throws Exception {
        // 응답만 고치고 저장을 안 하면 다음 조회에서 갈 수 없는 일정이 되살아난다.
        weatherClient.respondByDate(date -> Optional.empty());
        trainArrivesAt(LocalDateTime.of(2026, 9, 11, 8, 30));
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(transitTwoDayBody("2026-09-11")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        trainArrivesAt(LocalDateTime.of(2026, 9, 21, 2, 0));
        mockMvc.perform(patch(URL + "/{id}", courseId).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/{id}", courseId).header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소2"));
    }

    @Test
    void 도착이_그대로면_첫날을_건드리지_않는다() throws Exception {
        weatherClient.respondByDate(date -> Optional.empty());
        trainArrivesAt(LocalDateTime.of(2026, 9, 11, 8, 30));
        String guest = uniqueGuest();
        String saved = mockMvc.perform(post(URL).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content(transitTwoDayBody("2026-09-11")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");

        trainArrivesAt(LocalDateTime.of(2026, 9, 20, 8, 30)); // 옮긴 날짜에도 오전 도착

        mockMvc.perform(patch(URL + "/{id}", courseId).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstDayChange").doesNotExist())
                .andExpect(jsonPath("$.data.days.length()").value(2));
    }
}
