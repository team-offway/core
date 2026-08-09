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
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import com.offway.core.weather.infrastructure.airkorea.AirKoreaClient;
import com.offway.core.weather.infrastructure.airkorea.StubAirKoreaClient;
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
    private StubAirKoreaClient airKoreaClient;

    @Autowired
    private com.offway.core.trip.service.RegionRankingService regionRankingService;

    @Autowired
    private com.offway.core.trip.service.RegionContentProvider regionContentProvider;

    @Autowired
    private com.offway.core.trip.service.RegionContentRefreshService regionContentRefreshService;

    @Autowired
    private com.offway.core.weather.service.AirQualityService airQualityService;

    // 랭킹·콘텐츠 캐시는 공유 싱글톤 — 각 테스트가 자기 stub 시나리오를 타도록 캐시를 비운다(DB 롤백에 준하는 격리).
    @org.junit.jupiter.api.BeforeEach
    void evictCaches() {
        regionRankingService.evictCache();
        regionContentProvider.evictCache();
        airQualityService.evictCache();
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

        @Bean
        @Primary
        AirKoreaClient stubAirKoreaClient() {
            return new StubAirKoreaClient();
        }
    }

    /** 볼거리가 충분한 지역 콘텐츠 — 대표 이미지·categories(NA→관광지). */
    private static TourPoiResult content() {
        TourPoi poi = new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null);
        return new TourPoiResult(List.of(poi), 38);
    }

    @Test
    void 남은연차_필터칩_추천지역을_콘텐츠와_함께_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        tourApiClient.respond(HomeIntegrationTest::content);
        // 요청 경로는 저장된 콘텐츠만 읽는다(#193) — stub 을 세팅한 뒤 적재를 거친다.
        regionContentRefreshService.refresh();
        // 홈은 대기질을 부르지 않는다 — 부르면 여기서 깨진다. 예전엔 카드마다 시도별로 물어 느린 시도를
        // 만나면 홈이 24초 걸렸고, 그래서 코스로 옮겼다. 회귀하면 이 stub 이 먼저 잡는다.
        airKoreaClient.respond(() -> {
            throw new AssertionError("홈이 대기질을 조회했습니다 — 요청 경로에서 빠져 있어야 합니다");
        });

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
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6))
                .andExpect(jsonPath("$.data.recommendedRegions[0].name").exists())
                .andExpect(jsonPath("$.data.recommendedRegions[0].crowdLevel").value("LOW"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].categories[0].key").value("SIGHT"))
                // 전 지역이 인구감소지역 → 대표 혜택으로 반값여행 뱃지
                .andExpect(jsonPath("$.data.recommendedRegions[0].benefit.text").value("여행경비 50% 환급"))
                .andExpect(jsonPath("$.data.recommendedRegions[0].benefit.policyType").value("REGIONAL_VOUCHER"))
                // 대기질은 홈 계약에서 빠졌다 — 실시간 값이라 "다음 주 어디 갈까" 를 고르는 자리에 쓸모가 없고,
                // 그것 하나 때문에 홈이 외부 호출을 물고 있었다.
                .andExpect(jsonPath("$.data.recommendedRegions[0].airQuality").doesNotExist());
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
        airKoreaClient.respond(Optional::empty);

        mockMvc.perform(get(URL).header("X-Guest-Id", GUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6));
    }
}
