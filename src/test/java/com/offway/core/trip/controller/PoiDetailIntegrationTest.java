package com.offway.core.trip.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class PoiDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTourApiClient tourApiClient;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    @Test
    void 장소_상세를_운영시간과_함께_200으로_내린다() throws Exception {
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "완도타워", "전남 완도군", "061-1", 34.3, 126.7, "http://img/1.jpg", "전망대 소개")));
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder().contentId("126508").useTime("09:00~18:00").restDate("연중무휴").parking("가능").build()));

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.title").value("완도타워"))
                .andExpect(jsonPath("$.data.typeLabel").value("관광지")) // contentTypeId 12 → 관광지
                .andExpect(jsonPath("$.data.imageUrl").value("http://img/1.jpg"))
                .andExpect(jsonPath("$.data.sight.useTime").value("09:00~18:00"))
                .andExpect(jsonPath("$.data.sight.restDate").value("연중무휴"))
                .andExpect(jsonPath("$.data.sight.parking").value("가능"))
                // 관광지가 아닌 블록은 null 이다 — 클라이언트가 자기 카테고리만 보면 되게.
                //
                // doesNotExist() 를 쓰지 않는다. 그건 "non-null 값이 없다" 만 보므로 필드가 null 로 실린 것과
                // 아예 빠진 것을 구분하지 못한다. 이 응답은 NON_NULL 이 아니라 null 로 나가는 것이 계약이고
                // (PoiApi 도 그렇게 문서화한다), 나중에 필드가 사라져도 doesNotExist() 는 그대로 통과한다.
                .andExpect(jsonPath("$.data.food").value(nullValue()))
                .andExpect(jsonPath("$.data.stay").value(nullValue()));
    }

    @Test
    void 음식점이면_대표메뉴와_영업시간이_food_블록으로_나간다() throws Exception {
        // 영업시간·휴무일·대표메뉴는 우리 89곳에서 실측 95~100% 로 채워진다 — 안 읽고 버릴 값이 아니다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "111", 39, "벽오동", "경북 청도군", "054-1", 35.6, 128.7, "http://img/f.jpg", "한우 전문점")));
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder()
                .contentId("111")
                .useTime("11:30~21:00 (마지막 주문 20:00)")
                .restDate("매주 월요일")
                .signatureMenu("벽오동 스페샬")
                .menus("토시살 / 꽃살 / 갈비탕")
                .build()));

        mockMvc.perform(get("/api/v1/pois/{id}", "111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeLabel").value("음식점"))
                .andExpect(jsonPath("$.data.food.openTime").value("11:30~21:00 (마지막 주문 20:00)"))
                .andExpect(jsonPath("$.data.food.restDate").value("매주 월요일"))
                .andExpect(jsonPath("$.data.food.signatureMenu").value("벽오동 스페샬"))
                .andExpect(jsonPath("$.data.food.menus").value("토시살 / 꽃살 / 갈비탕"))
                .andExpect(jsonPath("$.data.sight").value(nullValue()));
    }

    @Test
    void 숙소면_입퇴실_시각과_객실수가_stay_블록으로_나간다() throws Exception {
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "222", 32, "더스터닝", "경북 안동시", "054-2", 36.5, 128.7, "http://img/s.jpg", "펜션")));
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder()
                .contentId("222").checkIn("16:00").checkOut("11:00").roomCount("11")
                .reservation("전화(010-3809-6277)").build()));

        mockMvc.perform(get("/api/v1/pois/{id}", "222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stay.checkIn").value("16:00"))
                .andExpect(jsonPath("$.data.stay.checkOut").value("11:00"))
                .andExpect(jsonPath("$.data.stay.roomCount").value("11"))
                .andExpect(jsonPath("$.data.food").value(nullValue()));
    }

    @Test
    void 문화시설이면_요금이_culture_블록으로_나간다() throws Exception {
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "333", 14, "신안갯벌박물관", "전남 신안군", "061-3", 34.8, 126.1, "http://img/c.jpg", "박물관")));
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder()
                .contentId("333").useTime("평일 09:00~17:00").restDate("매주 월요일")
                .fee("무료").parking("가능").build()));

        mockMvc.perform(get("/api/v1/pois/{id}", "333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.culture.fee").value("무료"))
                .andExpect(jsonPath("$.data.culture.useTime").value("평일 09:00~17:00"))
                .andExpect(jsonPath("$.data.sight").value(nullValue()));
    }

    @Test
    void 보조정보가_없으면_어떤_블록도_만들지_않는다() throws Exception {
        // 우리 DB 출처(인허가·국가유산)나 관광 API 가 소개정보를 안 주는 경우. 빈 블록을 만들면
        // 화면이 "정보 있음" 으로 읽고 빈 줄을 그린다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "444", 12, "어느 명소", "강원 정선군", null, 37.3, 128.6, null, null)));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sight").value(nullValue()))
                .andExpect(jsonPath("$.data.food").value(nullValue()))
                .andExpect(jsonPath("$.data.stay").value(nullValue()))
                .andExpect(jsonPath("$.data.culture").value(nullValue()))
                .andExpect(jsonPath("$.data.leports").value(nullValue()));
    }

    @Test
    void 캐치프레이즈가_있는_장소면_data에_함께_내린다() throws Exception {
        // 126508 은 시드 CSV(구석구석 캐치프레이즈)에 실제 존재한다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "경복궁", "서울 종로구", null, 37.5, 126.9, null, null)));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catchphrase").isNotEmpty());
    }

    @Test
    void 없는_장소면_404_TOUR_003() throws Exception {
        tourApiClient.respondDetail(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR-003"));
    }
}
