package com.offway.core.trip.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeIntegrationTest {

    private static final String URL = "/api/v1/home";

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
    private com.offway.core.weather.service.AirQualityService airQualityService;

    // 랭킹·콘텐츠·대기질 캐시는 공유 싱글톤 — 각 테스트가 자기 stub 시나리오를 타도록 캐시를 비운다(DB 롤백에 준하는 격리).
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
        airKoreaClient.respond(() -> Optional.of(new AirQuality(45, 23, AirGrade.MODERATE)));

        mockMvc.perform(get(URL).param("remainingLeave", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(13))
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
                // 지역 시도 실시간 대기질
                .andExpect(jsonPath("$.data.recommendedRegions[0].airQuality.pm10").value(45))
                .andExpect(jsonPath("$.data.recommendedRegions[0].airQuality.pm25").value(23))
                .andExpect(jsonPath("$.data.recommendedRegions[0].airQuality.grade").value("보통"));
    }

    @Test
    void 남은연차가_없어도_200으로_내려준다() throws Exception {
        dataLabClient.respond(TourVisitorResult::empty);
        tourApiClient.respond(HomeIntegrationTest::content);
        // 대기질 조회 실패해도(빈 값) 카드는 나온다 — 부가 정보라 airQuality=null
        airKoreaClient.respond(Optional::empty);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("게스트"))
                .andExpect(jsonPath("$.data.user.remainingLeaveDays").value(nullValue()))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6))
                .andExpect(jsonPath("$.data.recommendedRegions[0].airQuality").value(nullValue()));
    }

    @Test
    void 관광빅데이터_조회가_실패해도_홈은_200으로_추천을_내린다() throws Exception {
        // 생활인구는 랭킹 가중치일 뿐 — 조회가 실패해도 방문자 0 폴백 랭킹으로 홈은 떠야 한다(502 금지)
        dataLabClient.respond(() -> {
            throw TourApiException.dataLabLookupFailed(new RuntimeException("upstream down"));
        });
        tourApiClient.respond(HomeIntegrationTest::content);
        airKoreaClient.respond(Optional::empty);

        mockMvc.perform(get(URL).param("remainingLeave", "13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.recommendedRegions.length()").value(6));
    }
}
