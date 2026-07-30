package com.offway.core.trip.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.infrastructure.datalab.StubTourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    @Autowired
    private com.offway.core.trip.service.RegionRankingService regionRankingService;

    @Autowired
    private com.offway.core.trip.service.RegionContentProvider regionContentProvider;

    // 랭킹·콘텐츠 캐시는 공유 싱글톤 — 각 테스트가 자기 stub 시나리오를 타도록 캐시를 비운다(DB 롤백에 준하는 격리).
    @org.junit.jupiter.api.BeforeEach
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

    /** 볼거리가 충분한(확장 안 하는) 지역 콘텐츠 한 건 — 대표 이미지·categories(NA→관광지) 포함. */
    private static TourPoiResult sufficientContent() {
        TourPoi poi = new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null);
        return new TourPoiResult(List.of(poi), 38);
    }

    /** 관측 창과 같은 7일. 지역별 일 방문자수를 그대로 반복해 일평균이 곧 그 값이 되게 한다(혼잡도 뱃지 계산이 자명해짐). */
    private static final int OBSERVE_DAYS = 7;

    /**
     * 법정 시군구코드별 <b>일</b> 방문자수로 관광빅데이터 응답을 만든다. {@code signguNm} 에 실제 지명을 넣어 지명이 겹치는
     * 상황을 그대로 재현한다 — 집계가 지명을 키로 쓰면 서로의 방문자가 합산돼 뱃지가 틀어진다.
     */
    private static TourVisitorResult visitorsPerDay(Map<String, Double> dailyByLegalCode, Map<String, String> names) {
        List<RegionVisitor> items = new ArrayList<>();
        dailyByLegalCode.forEach((legalCode, daily) -> {
            for (int day = 0; day < OBSERVE_DAYS; day++) {
                items.add(new RegionVisitor(
                        legalCode,
                        names.get(legalCode),
                        LocalDate.of(2026, 6, 24).plusDays(day),
                        VisitorType.DOMESTIC,
                        daily));
            }
        });
        return new TourVisitorResult(items, items.size());
    }

    /**
     * 지명이 겹치는 한 쌍 — 집계 대상(target)과, 같은 지명을 쓰는 다른 지역(leak).
     *
     * @param sharedName 두 지역이 공유하는 시군구 지명
     * @param targetCode 단언 대상의 법정 시군구코드
     * @param targetName 응답의 {@code name}("시군구 · 시도")
     * @param leakCode 같은 지명을 쓰는 다른 지역의 코드 — 이 방문자가 target 으로 새면 안 된다
     * @param originLat 출발지 위도 — target 이 도달 후보에 들도록 근처로 잡는다
     * @param originLng 출발지 경도
     */
    private record NameCollision(
            String sharedName,
            String targetCode,
            String targetName,
            String leakCode,
            double originLat,
            double originLng) {}

    private static List<NameCollision> nameCollisions() {
        return List.of(
                // 대전 동구(30110)는 우리 89곳이 아니지만, 지명으로 집계하면 부산 동구가 그 방문자를 받는다.
                new NameCollision("동구", "26170", "동구 · 부산광역시", "30110", 35.1798, 129.0750),
                // 고성군은 강원(51820)·경남(48820) 둘 다 우리 89곳이라 서로의 방문자를 나눠 갖는다.
                new NameCollision("고성군", "51820", "고성군 · 강원특별자치도", "48820", 38.3803039, 128.4678610));
    }

    /**
     * 전국에 동구는 6곳, 중구는 6곳, 서구는 5곳, 남구·북구는 4곳, 고성군은 2곳이다. 지명으로 집계하면 서로 다른 지역의 방문자가
     * 한 버킷에 합산돼 뱃지·랭킹이 부풀려진다.
     *
     * <p>같은 지명을 쓰는 다른 지역에 일 50,000명, 단언 대상에 일 3,500명을 준다. 법정코드로 집계하면 대상은 일평균 3,500 →
     * {@code MID} 다. 지명으로 집계하면 53,500 이 돼 {@code HIGH} 로 틀어진다.
     *
     * <p>혼잡도 뱃지로 단언하는 이유: 뱃지는 <b>그 지역 자기 방문자수만</b>의 함수라, 랭킹 점수(베이지안 pooling·정렬)와
     * 무관하게 집계 키가 맞는지를 직접 드러낸다.
     *
     * <p>도달 한계를 좁게 잡는 이유: 후보 상한(20건) 절단에 걸리면 대상이 응답에서 빠져 단언이 무의미해진다. 도달 필터가 랭킹보다
     * 앞에 있어, 반경을 좁히면 후보가 상한 미만으로 줄어 순위와 무관하게 대상이 응답에 남는다.
     */
    @ParameterizedTest
    @MethodSource("nameCollisions")
    void 지명이_겹치는_지역끼리_방문자수가_섞이지_않는다(NameCollision collision) throws Exception {
        dataLabClient.respond(() -> visitorsPerDay(
                Map.of(collision.targetCode(), 3_500.0, collision.leakCode(), 50_000.0),
                Map.of(collision.targetCode(), collision.sharedName(),
                        collision.leakCode(), collision.sharedName())));
        tourApiClient.respond(RegionRecommendIntegrationTest::sufficientContent);

        String body = """
                { "originLat": %s, "originLng": %s, "transport": "CAR", "maxReachMinutes": 30 }"""
                .formatted(collision.originLat(), collision.originLng());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[?(@.name == '%s')].crowdLevel".formatted(collision.targetName()))
                        .value(org.hamcrest.Matchers.contains("MID")));
    }

    /**
     * 관광빅데이터는 완결된 달만 월 단위로 발행된다. 지난달이 아직 미발행이면 조회는 <b>예외가 아니라</b>
     * {@code resultCode=0000} + 빈 결과로 성공하므로, 이전 달로 물러서지 않으면 전 지역 방문자가 0이 돼 랭킹이 무의미해진다.
     */
    @Test
    void 지난달이_미발행이면_이전_달로_물러서_집계한다() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        dataLabClient.respond(() -> attempts.getAndIncrement() < 2
                ? TourVisitorResult.empty()
                : visitorsPerDay(Map.of("26170", 3_500.0), Map.of("26170", "동구")));
        tourApiClient.respond(RegionRecommendIntegrationTest::sufficientContent);

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                // 세 번째 달(2회 미발행 뒤)의 데이터가 실제로 집계에 반영됐다
                .andExpect(jsonPath("$.data.regions[?(@.name == '동구 · 부산광역시')].crowdLevel")
                        .value(org.hamcrest.Matchers.contains("MID")));
        assertEquals(3, attempts.get(), "빈 결과를 만나면 이전 달로 물러서야 한다");
    }

    /** 집계 전체 상한(현재 60초) — 서비스가 소유하므로 여기서 상한값을 직접 참조하지 않고 이 상수로 느슨하게 검증한다. */
    private static final Duration AGGREGATE_DEADLINE_CEILING = Duration.ofSeconds(60);

    /**
     * 집계 전체 시간 상한이 <b>클라이언트 한 건까지</b> 실제로 내려가는지. 안 내려가면 예산이 거의 없는 시점에 시작한 마지막
     * 요청이 클라이언트 자체 timeout(20초)만큼 더 대기해, 집계 상한이 그만큼 초과된다.
     *
     * <p>클라이언트가 짧은 쪽을 따르는지는 단위 테스트({@code TourDataLabClientImplTest})가 본다. 여기서는 서비스가
     * 예산을 <b>넘기기는 하는지</b>(배선)를 본다 — 두 곳이 다 맞아야 상한이 성립한다.
     */
    @Test
    void 집계_시간_예산이_클라이언트까지_전달된다() throws Exception {
        dataLabClient.respond(() -> visitorsPerDay(Map.of("26170", 3_500.0), Map.of("26170", "동구")));
        tourApiClient.respond(RegionRecommendIntegrationTest::sufficientContent);

        String body = """
                { "originLat": 35.1798, "originLng": 129.0750, "transport": "CAR", "maxReachMinutes": 30 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        Duration budget = dataLabClient.lastMaxWait();
        assertNotNull(budget, "서비스가 남은 시간 예산을 넘겨야 한다 — null 이면 배선이 빠졌다");
        assertTrue(budget.isPositive(), "예산은 양수여야 한다. 실제=" + budget);
        assertTrue(
                budget.compareTo(AGGREGATE_DEADLINE_CEILING) <= 0,
                "예산이 집계 상한을 넘을 수 없다. 실제=" + budget);
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
    void 관광빅데이터_조회가_실패해도_200으로_추천을_내린다() throws Exception {
        // 생활인구는 랭킹 가중치 — 조회 실패 시 방문자 0 폴백 랭킹으로 추천을 유지한다(502 금지)
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });
        tourApiClient.respond(RegionRecommendIntegrationTest::sufficientContent);

        String body = """
                { "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 100000 }""";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
