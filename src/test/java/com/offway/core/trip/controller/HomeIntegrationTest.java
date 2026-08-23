package com.offway.core.trip.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.domain.RegionPoi;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.List;
import java.util.Optional;
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
class HomeIntegrationTest {

    private static final String URL = "/api/v1/home";
    private static final String GUEST = "home-guest";

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

    @Autowired
    private com.offway.core.trip.service.RegionContentRefreshService regionContentRefreshService;

    @Autowired
    private com.offway.core.trip.repository.RegionPoiRepository regionPoiRepository;

    @Autowired
    private com.offway.core.trip.repository.PoiIntroRepository poiIntroRepository;

    @Autowired
    private com.offway.core.region.repository.RegionRepository regionRepository;

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

    /** 볼거리가 충분한 지역 콘텐츠 — 대표 이미지·categories(NA→관광지). */
    private static TourPoiResult content() {
        TourPoi poi = new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null, null);
        return new TourPoiResult(List.of(poi), 38);
    }

    @Test
    void 남은연차_필터칩_추천지역을_콘텐츠와_함께_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        tourApiClient.respond(HomeIntegrationTest::content);
        // 요청 경로는 저장된 콘텐츠만 읽는다(#193) — stub 을 세팅한 뒤 적재를 거친다.
        regionContentRefreshService.refresh();
        // 홈의 남은 연차는 저장값에서 온다(#89) — 클라이언트가 넘긴 값을 되돌려주던 예전과 다르다.
        // 그래서 먼저 저장해야 13 이 나온다. 이 두 단계가 실제 배선을 함께 검증한다.
        mockMvc.perform(patch("/api/v1/leaves/me")
                        .header("X-Guest-Id", GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalDays\": 13}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(13.0))
                .andExpect(jsonPath("$.data.filters.length()").value(5))
                .andExpect(jsonPath("$.data.filters[0].key").value("ALL"))
                // 칩 개수를 함께 내린다(#266) — 앱이 "전부 1건" 으로 채우던 자리다. 89곳 전부에 관광지(NA)
                // 콘텐츠를 적재했으므로 ALL·SIGHT 가 둘 다 89 다.
                .andExpect(jsonPath("$.data.filters[0].regionCount").value(89))
                .andExpect(jsonPath("$.data.filters[1].key").value("SIGHT"))
                .andExpect(jsonPath("$.data.filters[1].regionCount").value(89))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6))
                .andExpect(jsonPath("$.data.recommendedRegions[0].name").exists())
                .andExpect(jsonPath("$.data.recommendedRegions[0].crowdLevel").value("LOW"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].categories[0].key").value("SIGHT"))
                // 혜택 뱃지 내용은 여기서 단언하지 않는다. 이 경로는 LocalDate.now() 로 매칭하는데,
                // 진행 중인 캠페인은 기간이 끝나면 사라져 실행 시점에 따라 값이 달라진다. 실제로 이 자리는
                // 반값여행 문구를 박아 두고 있었고, 대상 지역이 좁혀지자 곧바로 깨졌다(#217).
                // 어느 지역에 어떤 정책이 붙는가는 날짜를 고정한 PolicyMatchIntegrationTest 가 소유한다.
                //
                // 구조 단언도 두지 않는다. 홈의 혜택은 배열이 아니라 <b>단수 nullable</b>({@code benefit})이라,
                // 매칭이 없으면 필드 자체가 사라진다 — "있는지" 를 물어도 결국 날짜에 기대는 단언이 된다.
                // (배열인 지역 추천 쪽은 항상 실려서 RegionRecommendIntegrationTest 가 구조를 지킨다.)
                .andExpect(jsonPath("$.data.recommendedRegions[0].name").isNotEmpty());
    }

    @Test
    void 남은연차가_없어도_200으로_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        tourApiClient.respond(HomeIntegrationTest::content);
        // 요청 경로는 저장된 콘텐츠만 읽는다(#193) — stub 을 세팅한 뒤 적재를 거친다.
        regionContentRefreshService.refresh();

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6));
    }

    @Test
    void 관광빅데이터_조회가_실패해도_홈은_200으로_추천을_내린다() throws Exception {
        // 생활인구는 랭킹 가중치일 뿐 — 조회가 실패해도 방문자 0 폴백 랭킹으로 홈은 떠야 한다(502 금지)
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });
        tourApiClient.respond(HomeIntegrationTest::content);
        // 요청 경로는 저장된 콘텐츠만 읽는다(#193) — stub 을 세팅한 뒤 적재를 거친다.
        regionContentRefreshService.refresh();

        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6));
    }

    // ── 이번달 추천 여행지 — 장소 카드(#305)

    /**
     * <b>카드가 장소이고 부제가 카테고리별로 조합된다.</b>
     *
     * <p>이 섹션이 새로 필요한 이유가 여기 있다 — 예전에는 지역 카드 데이터를 이 자리에 써서 제목과
     * 오버레이에 지역명이 두 번 나왔고, 칩을 눌러도 같은 지역이 그대로 남았다.
     */
    @Test
    void 홈이_장소_카드를_부제와_함께_내린다() throws Exception {
        long regionId = anyRegionId();
        String food = "home-food-1";
        regionPoiRepository.replaceRegion(regionId, List.of(
                poi(regionId, food, Category.FOOD, "밀면집"),
                poi(regionId, "home-sight-1", Category.SIGHT, "이중섭거리")));
        poiIntroRepository.upsertAll(
                Map.of(PoiIntroRepository.ContentRef.of(food, 39),
                        PoiIntro.builder().signatureMenu("갈치조림정식").build()),
                LocalDateTime.now());

        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recommendedPlaces").isArray())
                // 부제는 칩마다 다른 필드에서 온다 — 맛집은 대표메뉴다.
                .andExpect(jsonPath("$.data.recommendedPlaces[?(@.poiContentId=='" + food + "')].subtitle")
                        .value(org.hamcrest.Matchers.hasItem("갈치조림정식")))
                .andExpect(jsonPath("$.data.recommendedPlaces[?(@.poiContentId=='" + food + "')].kind")
                        .value(org.hamcrest.Matchers.hasItem("FOOD")))
                // 카드가 장소라 지역명을 따로 싣는다.
                .andExpect(jsonPath("$.data.recommendedPlaces[?(@.poiContentId=='" + food + "')].regionName")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
    }

    /**
     * <b>부제 재료가 없으면 그 줄을 비운다 — 지어내지 않는다.</b>
     *
     * <p>실측상 캠핑장과 레포츠가 이 자리에 온다. 상세를 부르면 {@code resultCode} 는 성공인데 내용이
     * 비어 오므로, 그 장소는 이름·사진만 있는 카드가 된다.
     */
    @Test
    void 부제_재료가_없으면_null_로_내린다() throws Exception {
        long regionId = anyRegionId();
        String bare = "home-bare-1";
        regionPoiRepository.replaceRegion(regionId, List.of(poi(regionId, bare, Category.STAY, "느티담길캠핑장")));

        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedPlaces[?(@.poiContentId=='" + bare + "')].subtitle")
                        .value(org.hamcrest.Matchers.everyItem(nullValue())));
    }

    /**
     * <b>두 섹션이 함께 나간다.</b>
     *
     * <p>지금 API 를 버리는 것이 아니라 위 섹션을 더하는 것이다 — {@code recommendedRegions} 는
     * "이번 연차엔 여기 어때요?" 계약으로 그대로 남는다.
     */
    @Test
    void 장소_섹션과_지역_섹션이_함께_나간다() throws Exception {
        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedPlaces").exists())
                .andExpect(jsonPath("$.data.recommendedRegions").isArray());
    }

    private long anyRegionId() {
        List<com.offway.core.region.domain.Region> regions = regionRepository.findAll();
        org.junit.jupiter.api.Assertions.assertFalse(regions.isEmpty(), "지역 마스터가 비어 이 테스트가 성립하지 않는다");
        return regions.get(0).getId();
    }

    /** 사진이 있어야 카드에 실린다 — 없으면 회색 판이라 애초에 빠진다. */
    private static RegionPoi poi(long regionId, String contentId, Category category, String title) {
        return RegionPoi.builder()
                .regionId(regionId)
                .contentId(contentId)
                .contentTypeId(category == Category.FOOD ? 39 : 12)
                .category(category)
                .title(title)
                .imageUrl("http://img/" + contentId + ".jpg")
                .baseYm(YearMonth.now())
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
