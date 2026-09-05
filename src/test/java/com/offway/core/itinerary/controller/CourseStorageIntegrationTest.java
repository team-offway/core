package com.offway.core.itinerary.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.policy.domain.Policy;
import com.jayway.jsonpath.JsonPath;
import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.infrastructure.tago.StubTrainInfoClient;
import com.offway.core.transport.infrastructure.tago.StubTransitLegClient;
import com.offway.core.transport.infrastructure.tago.TransitLegClient;
import com.offway.core.transport.service.TransitDurationRefreshService;
import com.offway.core.transport.infrastructure.tago.TrainInfoClient;
import com.offway.core.transport.service.TrainRouteService;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import com.offway.core.user.config.WithLoginUser;
import com.offway.core.weather.domain.DailyWeather;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.KmaWeatherClient;
import com.offway.core.weather.infrastructure.kma.StubKmaWeatherClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 코스 저장·조회(#33) 통합 테스트.
 *
 * <p>DB 격리: 롤백 대신 <b>테스트마다 다른 사용자</b>로 요청한다 — 값 없는 {@link WithLoginUser} 가 매번 새 UUID 를
 * 넣으므로 "내 코스" 목록이 이전 실행·다른 테스트와 섞이지 않는다(#280 이전의 고유 게스트 ID 와 같은 역할).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
class CourseStorageIntegrationTest {

    private static final String URL = "/api/v1/courses";

    /** {@code SecurityConfig} · {@code JwtAuthenticationFilter} 가 쓰는 권한 이름 — 같은 값이어야 한다. */
    private static final String USER_AUTHORITY = "ROLE_USER";

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

    @Autowired
    private StubTransitLegClient transitLegClient;

    @Autowired
    private TransitDurationRefreshService transitDurationRefreshService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TrainInfoClient stubTrainInfoClient() {
            return new StubTrainInfoClient();
        }

        @Bean
        @Primary
        TransitLegClient stubTransitLegClient() {
            return new StubTransitLegClient();
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


    /** 로그인한 사용자(클래스 애노테이션이 넣은 사람)로 코스를 저장하고 courseId 를 준다. */
    private long save(String body) throws Exception {
        return save(body, testSecurityContext());
    }

    private long save(String body, RequestPostProcessor as) throws Exception {
        String saved = mockMvc.perform(post(URL).with(as)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(saved, "$.data.courseId")).longValue();
    }

    @Test
    void 코스를_저장하면_201로_courseId를_준다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.courseId").isNumber())
                .andExpect(jsonPath("$.data.regionId").value(16))
                .andExpect(jsonPath("$.data.days[0].items[0].travelMinutes").value(0));
    }

    @Test
    void 저장한_코스가_내_코스_목록과_상세에_나온다() throws Exception {
        long courseId = save(VALID_BODY);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].placeCount").value(2));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.days[0].items.length()").value(2));
    }

    @Test
    void 남의_코스는_상세로_볼_수_없다_404() throws Exception {
        long courseId = save(VALID_BODY, loginAs(UUID.randomUUID()));

        // 다른 사용자가 같은 courseId 를 조회 → 존재 여부를 흘리지 않도록 404
        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 내_코스_목록에_남의_코스는_섞이지_않는다() throws Exception {
        save(VALID_BODY, loginAs(UUID.randomUUID()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 인증_없이_부르면_401이다() throws Exception {
        // 소유자를 요청 헤더가 아니라 인증이 정한다(#280) — 인증이 없으면 대상을 정할 수 없다.
        // 예전에는 게스트 헤더가 없거나 공백이면 400 이었다. 그 형식 검증 자체가 사라졌다.
        mockMvc.perform(get(URL).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"));

        mockMvc.perform(post(URL).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    @Test
    void 교통_거점_칸은_장소_식별자_없이_저장되고_그대로_돌아온다() throws Exception {
        // 대중교통 코스는 역·터미널로 시작해 역·터미널로 끝난다(#415). 그 칸에는 장소 상세 키가 없다.
        String body = """
                { "regionId": 16, "density": "PACKED", "transport": "TRANSIT", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"ARRIVAL","title":"정선역","lat":37.38,"lng":128.66,"travelMinutes":0},
                    {"order":2,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":22},
                    {"order":3,"timeOfDay":"MORNING","kind":"DEPARTURE","title":"정선역","lat":37.38,"lng":128.66,"travelMinutes":22}
                  ]}
                ]}""";

        long courseId = save(body);

        // 목록 카드의 "N곳" 은 장소만 센다 — 역·터미널을 함께 세면 대중교통 코스만 부풀어 보인다
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].placeCount").value(1));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].items[0].kind").value("ARRIVAL"))
                .andExpect(jsonPath("$.data.days[0].items[0].categoryLabel").value("도착"))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("정선역"))
                .andExpect(jsonPath("$.data.days[0].items[0].poiContentId").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[2].kind").value("DEPARTURE"))
                .andExpect(jsonPath("$.data.days[0].items[2].poiContentId").doesNotExist())
                // 출처는 실제로 실린 것만 적는다(#399). 역·터미널은 식별자가 없어 집계에서 빠지고,
                // 장소 하나(TourAPI)만 남는다 — 접두어 없는 값으로 읽혀 엉뚱한 기관이 붙지 않는지 본다.
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].key").value("KTO"));
    }

    @Test
    void 자차_코스에_교통_거점_칸을_보내면_400이다() throws Exception {
        // 생성은 대중교통일 때만 그 칸을 세우지만, 저장은 클라이언트가 보낸 것을 그대로 받는다(#415).
        // 막지 않으면 "역에서 시작하는 자차 코스" 가 저장된다.
        String invalid = """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"ARRIVAL","title":"정선역","lat":37.38,"lng":128.66,"travelMinutes":0},
                    {"order":2,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":22}
                  ]}
                ]}""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 장소_칸에_식별자가_없으면_400이다() throws Exception {
        // 종류를 봐야 필수인지 정해지므로 필드에 @NotBlank 를 못 건다. 그래도 계약 위반은 400 이어야 한다 —
        // 도메인에만 맡기면 클라이언트 실수가 500 으로 나간다.
        String invalid = """
                { "regionId": 16, "density": "PACKED", "transport": "CAR", "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","title":"장소1","lat":37.5,"lng":128.6,"travelMinutes":0}
                  ]}
                ]}""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
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

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 없는_코스_상세는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 코스를_삭제하면_목록과_상세에서_사라진다() throws Exception {
        long courseId = save(VALID_BODY);

        // 204 를 쓰지 않는다 — 응답 래퍼가 항상 body 를 만든다.
        mockMvc.perform(delete(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 남의_코스는_삭제할_수_없고_그대로_남는다_404() throws Exception {
        UUID owner = UUID.randomUUID();
        long courseId = save(VALID_BODY, loginAs(owner));

        // 403 이 아니라 404 — 403 이면 "그 ID 는 존재한다" 를 알려주는 셈이다.
        mockMvc.perform(delete(URL + "/{id}", courseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));

        // 거부로 끝나야 한다 — 주인 것이 지워졌으면 안 된다
        mockMvc.perform(get(URL + "/{id}", courseId).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(courseId));
    }

    @Test
    void 없는_코스_삭제는_404_ITINERARY_003() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_두_번_삭제하면_두_번째는_404() throws Exception {
        long courseId = save(VALID_BODY);

        mockMvc.perform(delete(URL + "/{id}", courseId))
                .andExpect(status().isOk());
        // 더블클릭·재시도 — 이미 없으니 없는 코스와 같은 답이다
        mockMvc.perform(delete(URL + "/{id}", courseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITINERARY-003"));
    }

    @Test
    void 같은_코스를_동시에_삭제해도_500이_나지_않는다() throws Exception {
        // 인증을 요청 단위로 붙인다 — 클래스의 @WithLoginUser 는 현재 스레드 전용이라 아래 풀 스레드엔 안 닿는다.
        // 그래서 이 테스트만 사용자를 직접 만들어 저장·삭제를 같은 사람으로 맞춘다.
        RequestPostProcessor owner = loginAs(UUID.randomUUID());
        long courseId = save(VALID_BODY, owner);

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
                        statuses.add(mockMvc.perform(delete(URL + "/{id}", courseId).with(owner))
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

    @Test
    void 저장한_코스_상세에도_날씨가_실린다() throws Exception {
        // 생성 응답에는 날씨가 실리는데 저장 코스 조회에는 빠져 있어 화면이 비어 있었다(#169).
        LocalDate travelDate = LocalDate.now().plusDays(1);
        weatherClient.respondByDate(date ->
                Optional.of(new DailyWeather(date, 18, 27, SkyState.RAIN, 80)));
        long courseId = save(bodyWithDateAndImage(travelDate));

        mockMvc.perform(get(URL + "/{id}", courseId))
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
        long courseId = save(VALID_BODY);

        // 키 자체가 빠진다 — "없는 값은 내려보내지 않는다"(응답 계약). null 로 내리지 않는다.
        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist());
    }

    @Test
    void 목록에_지역명이_실린다() throws Exception {
        // 없어서 FE 가 코스마다 상세를 한 번씩 더 불렀다(#171).
        save(bodyWithDateAndImage(LocalDate.now().plusDays(1)));

        mockMvc.perform(get(URL))
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
        // 주인은 클래스의 @WithLoginUser 가 정한다(#280) — 두 코스가 같은 사람의 것이어야
        // 목록에 함께 나오고, 그래야 사진이 갈리는지 볼 수 있다.
        save(bodyWithDateAndImage(LocalDate.now().plusDays(1)));
        save(VALID_BODY);

        String body = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        // 값이 아니라 카드 객체를 읽는다. `$.data[*].coverImageUrl` 로 값만 뽑으면 <b>키가 없는 카드가
        // 결과에서 조용히 빠져</b>, 한쪽만 필드를 잃어도 남은 하나로 distinct 가 1 이 된다. 지금은 이 응답이
        // null 도 그대로 직렬화해 그런 일이 없지만, 옆 파일 CourseResponse 가 @JsonInclude(NON_NULL) 을
        // 쓰고 있어 누가 여기에도 붙이는 순간 이 단언이 조용히 약해진다.
        List<Map<String, Object>> cards = JsonPath.read(body, "$.data[*]");
        assertTrue(cards.stream().allMatch(card -> card.containsKey("coverImageUrl")),
                "카드에 coverImageUrl 키가 없다 — 앱은 이 필드를 1순위로 읽는다: " + cards);

        List<Object> covers = cards.stream().map(card -> card.get("coverImageUrl")).toList();
        // 지역 대표 사진을 못 고른 지역이면 둘 다 null 이다 — 그때도 "갈리지 않는다" 는 성질은 지켜진다.
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
        long courseId = save(bodyWithDateAndImage(LocalDate.now().plusDays(30)));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].weather").doesNotExist())
                // 날씨가 없어도 코스는 그대로 나간다 — 부가 정보다
                .andExpect(jsonPath("$.data.days.length()").value(2));
    }

    /**
     * 정선(16) 근처로 도착하는 열차 — 저장 코스에서 다시 계산될 때 쓰인다.
     *
     * <p><b>출발이 09:00 이라야 한다.</b> 예전에는 06:00 이었는데 기본 출발시각(연차 = 08:00)보다 일러
     * {@code fastestDepartingFrom} 이 걸러냈다 — 이름은 "열차가 온다" 인데 실제로는 <b>그날 탈 수 있는 편이
     * 없는</b> 경로를 검증하고 있었다(#97 에서 발견).
     */
    private void trainArrives() {
        trainInfoClient.respond(() -> new TrainAvailability.Available(List.of(TrainLeg.of("KTX",
                LocalDateTime.of(2026, 9, 11, 9, 0),
                LocalDateTime.of(2026, 9, 11, 11, 30)))));
        trainRouteService.evictCache();
    }

    /** 역은 있는데 그날 탈 편이 없는 상황 — 도착 지점을 무엇으로 잡는지가 갈리는 자리다(#97). */
    private void trainDoesNotRun() {
        trainInfoClient.respond(TrainAvailability.NoServiceOnDate::new);
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
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainAccess.toStation").value("정선"))
                // 새 필드도 같은 값을 담는다 — 옛 필드를 걷어낼 때 화면이 비지 않게(#97)
                .andExpect(jsonPath("$.data.transitAccess.mode").value("TRAIN"))
                .andExpect(jsonPath("$.data.transitAccess.modeLabel").value("열차"))
                .andExpect(jsonPath("$.data.transitAccess.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"));
    }

    @Test
    void 그날_탈_열차가_없으면_더_가까운_버스_터미널을_도착_지점으로_잡는다() throws Exception {
        // 예전에는 역이 그날 안 다녀도 역 좌표를 동선 기준점으로 썼다. 정선은 터미널이 읍내에 있어
        // 역보다 가깝다 — 먼 역을 기준으로 잡으면 지역 반대편부터 코스를 짠다(#97 · #127).
        //
        // 고속인지 시외인지는 여기서 중요하지 않다. 정선에는 둘 다 있고 코스가 알고 싶은 것은
        // "어디에 내리는가" 하나다. 지금은 시외 쪽이 더 가깝다 — 재지오코딩(#436) 으로 정선
        // 시외터미널이 읍내 제자리로 돌아오면서 고속보다 가까워졌다.
        trainDoesNotRun();
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("INTERCITY_BUS"))
                .andExpect(jsonPath("$.data.transitAccess.modeLabel").value("시외버스"))
                .andExpect(jsonPath("$.data.transitAccess.status").value("POINT_ONLY"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"))
                // 옛 필드는 열차만 담기로 했다 — 버스로 가는 코스에 "역 없음" 을 내리면 화면이 "못 간다" 고 말한다
                .andExpect(jsonPath("$.data.trainAccess").doesNotExist());
    }

    /**
     * <b>어디서 타는지도 함께 내린다</b>(#396).
     *
     * <p>버스·여객선은 도착 지점만 뜨고 출발 쪽이 비어, 열차·자차와 같은 카드가 수단에 따라 다른
     * 모양이 됐다. 정작 서버는 <b>이미 출발 터미널을 찾고 있었다</b> — 구간 소요시간을 물으려고
     * 해석해 놓고 이름만 버렸다.
     */
    @Test
    void 버스로_가는_코스에도_어디서_타는지_실린다() throws Exception {
        trainDoesNotRun();
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("INTERCITY_BUS"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"))
                // 출발지(서울)에서 <b>같은 종류</b>의 최근접 터미널. 값을 못 박는다 — exists() 로 두면
                // 도착지명이 들어와도 초록이라, 정작 확인하려는 "출발 쪽" 이 맞는지를 못 본다.
                //
                // 이름이 '고속' 인데 시외 목록에 있다 — TAGO 가 그렇게 준다. 한 건물에서 둘 다 취급하는
                // 터미널이라 양쪽 목록에 다른 이름으로 올라 있다.
                .andExpect(jsonPath("$.data.transitAccess.fromPlace").value("서울고속버스터미널(경부)"));
    }

    @Test
    void 대표_수단_옆에_이_지역에_닿는_다른_수단도_함께_내린다() throws Exception {
        // 대표 하나만 내리면, 열차로도 갈 수 있다는 걸 아는 사용자에게는 화면이 틀린 것으로 읽힌다.
        // 정선은 역과 터미널이 둘 다 있어 대표(버스) 옆에 열차가 대안으로 붙는다(#97).
        trainDoesNotRun();
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("INTERCITY_BUS"))
                .andExpect(jsonPath("$.data.transitAccess.alternatives[?(@.mode == 'TRAIN')]").exists())
                .andExpect(jsonPath("$.data.transitAccess.alternatives[?(@.mode == 'INTERCITY_BUS')]").doesNotExist());
    }

    @Test
    void 열차로_가는_코스에도_대안_키는_항상_있다() throws Exception {
        // 빈 배열이라 키가 없는 경우가 없다 — 화면이 null 검사를 하지 않아도 된다.
        trainArrives();
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("TRAIN"))
                .andExpect(jsonPath("$.data.transitAccess.alternatives").isArray())
                .andExpect(jsonPath("$.data.transitAccess.alternatives[?(@.mode == 'TRAIN')]").doesNotExist());
    }

    @Test
    void 버스로_가는_코스는_처음엔_소요시간이_없다가_배치가_잰_뒤_붙는다() throws Exception {
        // 구간 조회창이 오늘~+2일뿐이라 요청 시점에 물을 수 없다. 그래서 첫 조회는 자리만 만들고 넘어가고,
        // 배치가 채운 뒤부터 정확해진다(#107). 요청 경로에서 외부를 부르지 않는 것이 요점이다.
        trainDoesNotRun();
        long courseId = save(transitBody(true));
        clearMeasuredLegs();

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.status").value("POINT_ONLY"))
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").doesNotExist());

        transitLegClient.respond(() -> new TransitLegResult.Measured(new MeasuredLeg(150, 28_600, "우등")));
        transitDurationRefreshService.measurePending();

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").value(150));
    }

    @Test
    void 조회가_실패하면_미운행으로_굳히지_않고_다음_배치로_미룬다() throws Exception {
        // 키가 없거나 한도가 마른 날의 실패를 "이 구간은 원래 안 다님" 으로 적으면 멀쩡한 구간이
        // 영원히 소요시간 없이 남는다. 화면에는 아무 흔적도 안 남는 종류의 사고다.
        trainDoesNotRun();
        long courseId = save(transitBody(true));
        onlyThisLegPending(courseId);

        transitLegClient.respond(TransitLegResult.Unavailable::new);
        transitDurationRefreshService.measurePending();

        // 실패를 안 적었으므로 다음 배치가 같은 구간을 다시 잰다 — 이번엔 성공한다.
        transitLegClient.respond(() -> new TransitLegResult.Measured(new MeasuredLeg(150, 28_600, "우등")));
        transitDurationRefreshService.measurePending();

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").value(150));
    }

    @Test
    void 미운행이면_수단의_조회창만큼만_물어본다() throws Exception {
        // 하루만 물어 비면 "이 구간은 안 다닌다" 로 굳는데, 주 몇 편짜리 배차는 그렇게 사라진다.
        // 반대로 조회창 밖까지 밀면 어차피 0건인 날을 물어 외부 한도만 태운다.
        trainDoesNotRun();
        long courseId = save(transitBody(true));
        onlyThisLegPending(courseId);

        transitLegClient.respond(TransitLegResult.NoService::new);
        transitDurationRefreshService.measurePending();

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        assertIterableEquals(
                List.of(today, today.plusDays(1), today.plusDays(2)), // 정선은 버스 — 조회창이 사흘이다
                transitLegClient.askedDates());
    }

    @Test
    void 미운행으로_적힌_구간도_한참_뒤에는_다시_잰다() throws Exception {
        // 겨울에 쉬는 항로와 새로 뚫린 노선이 있다. 한 번의 조회로 영구히 굳히면, 배 말고 닿는 수단이
        // 없는 지역은 그대로 "도달 불가" 로 남는다 — 화면에는 아무 흔적도 안 남는 종류의 사고다.
        trainDoesNotRun();
        long courseId = save(transitBody(true));
        onlyThisLegPending(courseId);

        transitLegClient.respond(TransitLegResult.NoService::new);
        transitDurationRefreshService.measurePending();

        // 갓 적은 미운행은 다시 재지 않는다 — 매시 배치가 같은 구간을 계속 물으면 한도가 샌다.
        transitLegClient.respond(() -> new TransitLegResult.Measured(new MeasuredLeg(150, 28_600, "우등")));
        transitDurationRefreshService.measurePending();
        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").doesNotExist());

        // 적은 지 오래됐으면 다시 잰다.
        jdbcTemplate.update(
                "UPDATE transit_leg_duration SET measured_at = ? WHERE minutes IS NULL AND measured_at IS NOT NULL",
                LocalDateTime.now().minusDays(60));
        transitDurationRefreshService.measurePending();

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").value(150));
    }

    @Test
    void 첫날_연차_기록이_없는_옛_코스도_날짜를_고칠_수_있다() throws Exception {
        // start_day_leave 컬럼이 생기기 전에 저장된 코스는 이 값이 비어 있다. 그 코스의 날짜를 고치면
        // 첫날 재정렬이 NPE 로 터져 수정 자체가 500 이 됐다 — 옛 코스만 골라 못 고치는 상태였다.
        weatherClient.respondByDate(date -> Optional.empty());
        trainArrivesAt(LocalDateTime.of(2026, 9, 11, 8, 30));
        long courseId = save(transitTwoDayBody("2026-09-11"));
        jdbcTemplate.update("UPDATE course SET start_day_leave = NULL WHERE id = ?", courseId);

        trainArrivesAt(LocalDateTime.of(2026, 9, 20, 8, 30));

        mockMvc.perform(patch(URL + "/{id}", courseId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.travelDate").value("2026-09-20"));
    }

    /**
     * 앞선 테스트가 남긴 구간 측정값을 지운다.
     *
     * <p>통합 테스트는 컨텍스트를 공유하는데 <b>전 테스트가 같은 구간을 쓴다</b> — 출발지도 지역(정선)도
     * 같아 터미널 짝이 하나다. 지우지 않으면 "아직 안 잰 구간" 을 전제하는 테스트가 실행 순서에 따라 깨진다.
     */
    private void clearMeasuredLegs() {
        jdbcTemplate.update("DELETE FROM transit_leg_duration");
    }

    /** 이 코스의 구간 하나만 배치 대상으로 남긴다 — 그래야 "몇 건 물었나" 를 셀 수 있다. */
    private void onlyThisLegPending(long courseId) throws Exception {
        clearMeasuredLegs();
        mockMvc.perform(get(URL + "/{id}", courseId)).andExpect(status().isOk()); // 자리 만들기
    }

    private static String carBody(boolean withOrigin) {
        return carBody(withOrigin, null);
    }

    /** 출발지 이름을 함께 싣는 자차 코스(#382). {@code originName} 이 null 이면 그 줄을 안 보낸다. */
    private static String carBody(boolean withOrigin, String originName) {
        String name = originName == null ? "" : "\"originName\": \"%s\",".formatted(originName);
        String origin = withOrigin ? "\"originLat\": 37.5547, \"originLng\": 126.9707," + name : "";
        return """
                { "regionId": 16, "density": "PACKED", "transport": "CAR",
                  "travelDate": "2026-09-11", %s "days": [
                  { "day": 1, "items": [
                    {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
                  ]}
                ]}""".formatted(origin);
    }

    @Test
    void 자차_코스에도_도착_안내가_실린다() throws Exception {
        // 예전에는 대중교통에만 만들어 자차는 카드가 통째로 비었다. 그런데 같은 계산을 이미 하고 있었다 —
        // 후보지역 추천이 그 지역까지의 도달시간을 답하고, 사용자는 그 숫자를 보고 지역을 골랐다(#379).
        long courseId = save(carBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("CAR"))
                .andExpect(jsonPath("$.data.transitAccess.modeLabel").value("자차"))
                // 자차는 역·터미널이 아니라 지역 자체가 도착지다. 여기가 비면 화면이 카드를 접는다.
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"))
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").isNumber())
                .andExpect(jsonPath("$.data.transitAccess.distanceKm").isNumber())
                // 자차로 가기로 한 사람에게 "시외버스로도 갈 수 있다" 를 늘어놓지 않는다
                .andExpect(jsonPath("$.data.transitAccess.alternatives").isEmpty())
                // 옛 필드는 열차만 담기로 했다 — 자차가 여기 실리면 화면이 "역 없음" 으로 읽는다
                .andExpect(jsonPath("$.data.trainAccess").doesNotExist());
    }

    @Test
    void 자차_카드에_출발지_이름이_실린다() throws Exception {
        // 서버는 좌표를 이름으로 바꾸지 못한다 — 저장할 때 앱이 실어 보낸 값을 그대로 되돌려준다(#382).
        long courseId = save(carBody(true, "서울"));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("CAR"))
                .andExpect(jsonPath("$.data.transitAccess.fromPlace").value("서울"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"));
    }

    @Test
    void 출발지_이름을_안_보내도_자차_카드는_뜬다() throws Exception {
        // 지오코딩이 실패했거나 이 필드를 모르는 앱이다. 이름만 빠지고 시간·거리는 그대로 나간다.
        long courseId = save(carBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.mode").value("CAR"))
                .andExpect(jsonPath("$.data.transitAccess.fromPlace").doesNotExist())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").isNumber());
    }

    @Test
    void 이름이_너무_길어도_저장을_막지_않고_이름만_버린다() throws Exception {
        // 화면 한 줄을 채우는 곁가지 값 때문에 코스 담기가 실패하면 주객이 뒤집힌다.
        long courseId = save(carBody(true, "가".repeat(50)));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.fromPlace").doesNotExist())
                .andExpect(jsonPath("$.data.transitAccess.toPlace").value("정선"));
    }

    /**
     * 어디서 출발했는지 모르면 몇 분 걸리는지도 모른다 — <b>다만 그 사실을 말한다</b>(#422).
     *
     * <p>예전에는 여기서 {@code transitAccess} 가 통째로 빠졌다. 그러면 앱이 "이 값을 모르는 옛
     * 서버" 와 "서버가 답을 못 하는 코스" 를 구분할 수 없어, 화면이 왜 비었는지 알 방법이 없었다.
     */
    @Test
    void 출발지를_모르는_자차_코스는_도착_안내_대신_이유를_준다() throws Exception {
        // 이 필드가 생기기 전에 저장된 코스가 이 경우다.
        long courseId = save(carBody(false));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.status").value("ORIGIN_UNKNOWN"))
                .andExpect(jsonPath("$.data.transitAccess.toPlace").doesNotExist())
                .andExpect(jsonPath("$.data.transitAccess.durationMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.days.length()").value(1));
    }

    @Test
    void 대중교통_코스에도_출발지에서_내리는_곳까지의_거리가_실린다() throws Exception {
        // 화면이 "약 2시간 29분 · 200km" 로 소요시간 옆에 붙인다. 세 수단이 같은 카드라 여기서도 필요하다.
        trainArrives();
        long courseId = save(transitBody(true));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transitAccess.distanceKm").isNumber());
    }

    @Test
    void 출발지_없이_저장된_코스는_열차_접근이_비고_그게_오류가_아니다() throws Exception {
        // 이 필드가 생기기 전에 저장된 코스가 이 경우다. 근거가 없으니 지어내지 않는다.
        trainArrives();
        long courseId = save(transitBody(false));

        mockMvc.perform(get(URL + "/{id}", courseId))
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

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(latOnly))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 자차_코스는_출발지가_있어도_열차_접근이_없다() throws Exception {
        // 자차는 열차 접근이 개념적으로 없다.
        trainArrives();
        long courseId = save(transitBody(true).replace("\"TRANSIT\"", "\"CAR\""));

        mockMvc.perform(get(URL + "/{id}", courseId))
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
        long courseId = save(firstDayEmptyBody("2026-09-12", "2026-09-13"));

        // 예전에는 며칠째를 그대로 달력 위치로 봐서 9/11·9/12 로 하루씩 당겨졌다.
        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-12"))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-09-13"));
    }

    @Test
    void 날짜를_안_보내면_종전대로_며칠째를_달력_위치로_본다() throws Exception {
        // 이 필드가 생기기 전 연동이 그대로 도는지 — 하위 호환.
        long courseId = save(firstDayEmptyBody(null, null));

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-11"))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-09-12"));
    }

    @Test
    void 여행_시작일보다_앞선_날짜는_400이다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITINERARY-002"));
    }

    @Test
    void 여행_기간을_넘는_날짜는_400이다() throws Exception {
        // 2026-09-11 시작 3일이면 9/13 이 마지막이다. 9/14 는 종료일 뒤라 앞뒤가 안 맞는다(#164).
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
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
        String body = VALID_BODY.replace(
                "{ \"regionId\": 16,", "{ \"travelDate\": \"" + travelDate + "\", \"regionId\": 16,");

        String saved = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int courseId = JsonPath.read(saved, "$.data.courseId");
        int onSave = ((List<?>) JsonPath.read(saved, "$.data.benefits")).size();

        String detail = mockMvc.perform(get(URL + "/{id}", courseId))
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
        long courseId = save(transitTwoDayBody("2026-09-11"));

        trainArrivesAt(LocalDateTime.of(2026, 9, 21, 2, 0)); // 옮긴 날짜의 막차 — 자정을 넘겨 닿는다

        mockMvc.perform(patch(URL + "/{id}", courseId)
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
        long courseId = save(transitTwoDayBody("2026-09-11"));

        trainArrivesAt(LocalDateTime.of(2026, 9, 21, 2, 0));
        mockMvc.perform(patch(URL + "/{id}", courseId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/{id}", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].items[0].title").value("장소2"));
    }

    @Test
    void 도착이_그대로면_첫날을_건드리지_않는다() throws Exception {
        weatherClient.respondByDate(date -> Optional.empty());
        trainArrivesAt(LocalDateTime.of(2026, 9, 11, 8, 30));
        long courseId = save(transitTwoDayBody("2026-09-11"));

        trainArrivesAt(LocalDateTime.of(2026, 9, 20, 8, 30)); // 옮긴 날짜에도 오전 도착

        mockMvc.perform(patch(URL + "/{id}", courseId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"travelDate\": \"2026-09-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstDayChange").doesNotExist())
                .andExpect(jsonPath("$.data.days.length()").value(2));
    }

    // ── 출발지 없이 저장된 코스(#422) ────────────────────────────────────

    /** 좌표를 뺀 대중교통 코스 — 앱 옛 버전이 보내던 모양이다. */
    private static final String TRANSIT_BODY_WITHOUT_ORIGIN = """
            { "regionId": 16, "density": "PACKED", "transport": "TRANSIT", "days": [
              { "day": 1, "items": [
                {"order":1,"timeOfDay":"MORNING","kind":"SIGHT","poiContentId":"c1","title":"장소1","lat":37.50,"lng":128.60,"travelMinutes":0}
              ]}
            ]}""";

    /**
     * <b>저장은 여전히 성공한다.</b>
     *
     * <p>400 으로 끊지 않는 이유 — 좌표를 안 싣는 옛 앱이 아직 있을 수 있고, 그러면 그 사용자는
     * 코스를 <b>아예 못 담는다</b>. 카드 한 줄이 비는 것보다 나쁘다.
     */
    @Test
    void 출발지가_없어도_저장은_성공한다() throws Exception {
        assertTrue(save(TRANSIT_BODY_WITHOUT_ORIGIN) > 0);
    }

    /**
     * <b>필드를 빼지 않는다</b> — 앱이 "옛 서버" 와 "답을 못 하는 코스" 를 구분해야 한다(#422).
     *
     * <p>예전에는 여기서 {@code transitAccess} 가 통째로 빠져(@JsonInclude NON_NULL), 화면이 왜
     * 비었는지 알 방법이 없었다. 실기기에서 그 상태를 재현하고서야 원인을 찾았다.
     */
    @Test
    void 출발지_없는_코스의_상세는_이유를_말한다() throws Exception {
        long courseId = save(TRANSIT_BODY_WITHOUT_ORIGIN);

        mockMvc.perform(get(URL + "/{id}", courseId).with(testSecurityContext()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.transitAccess").exists())
                .andExpect(jsonPath("$.data.transitAccess.status").value("ORIGIN_UNKNOWN"))
                // 무엇을 타는지는 출발지가 있어야 정해진다 — 지어내면 그게 거짓말이 된다.
                .andExpect(jsonPath("$.data.transitAccess.mode").doesNotExist())
                .andExpect(jsonPath("$.data.transitAccess.departures").isArray())
                .andExpect(jsonPath("$.data.transitAccess.departures").isEmpty());
    }
}
