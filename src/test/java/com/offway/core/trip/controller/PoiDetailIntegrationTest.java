package com.offway.core.trip.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceKind;
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

    @Autowired
    private com.offway.core.trip.service.PoiDetailService poiDetailService;

    @Autowired
    private com.offway.core.trip.repository.HeritagePlaceRepository heritagePlaceRepository;

    @Autowired
    private com.offway.core.trip.repository.LicensedPlaceRepository licensedPlaceRepository;

    /** 테스트 국가유산 풀이 채운 지역 — 경상북도 의성군. */
    private static final long UISEONG = 76L;

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
        poiDetailService.evictCache();
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
        poiDetailService.evictCache();
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
        poiDetailService.evictCache();
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
        poiDetailService.evictCache();
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
    void 레포츠면_요금과_이용시간이_leports_블록으로_나간다() throws Exception {
        poiDetailService.evictCache();
        // 다섯 블록 중 이것만 양수 검증이 없었다. 표본 24건이 전부 비어 있던 카테고리라
        // 값이 왔을 때의 매핑을 아무도 안 보고 있었다 — 정작 편차가 커서 값이 오면 그대로 쓰는 쪽이다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "444", 28, "완도 해양레포츠센터", "전남 완도군", "061-4", 34.3, 126.7, "http://img/l.jpg", "카약")));
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder()
                .contentId("444").useTime("09:00~17:20").restDate("연중무휴")
                .fee("성인 10,000원").parking("가능").build()));

        mockMvc.perform(get("/api/v1/pois/{id}", "444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leports.useTime").value("09:00~17:20"))
                .andExpect(jsonPath("$.data.leports.restDate").value("연중무휴"))
                .andExpect(jsonPath("$.data.leports.fee").value("성인 10,000원"))
                .andExpect(jsonPath("$.data.leports.parking").value("가능"))
                .andExpect(jsonPath("$.data.sight").value(nullValue()));
    }

    @Test
    void 보조정보가_없으면_어떤_블록도_만들지_않는다() throws Exception {
        poiDetailService.evictCache();
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
        poiDetailService.evictCache();
        // 126508 은 시드 CSV(구석구석 캐치프레이즈)에 실제 존재한다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "경복궁", "서울 종로구", null, 37.5, 126.9, null, null)));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catchphrase").isNotEmpty());
    }

    @Test
    void 국가유산_스팟은_우리_DB가_사진과_설명까지_답한다() throws Exception {
        poiDetailService.evictCache();
        // 코스에 국가유산이 나가기 시작했으므로 상세도 함께 답해야 한다. 이 분기가 없으면 `HER-` 식별자가
        // TourAPI 로 넘어가 404 가 난다 — 코스에는 있는데 누르면 없다고 하는 셈이다.
        tourApiClient.respondDetail(() -> {
            throw new AssertionError("국가유산 식별자를 TourAPI 에 물었다");
        });
        // 사진과 설명을 둘 다 단언하므로 후보도 둘 다 있는 것으로 고른다. 사진만 보고 고르면
        // "사진은 있고 설명은 없는" 건(적재분 기준 설명 98.9%)이 앞에 왔을 때 깨진다 — 풀 파일을
        // 갱신하는 순간 원인을 알기 어려운 실패가 된다.
        HeritagePlace heritage = heritagePlaceRepository.findVisitableCandidates(UISEONG, 10).stream()
                .filter(place -> place.getImageUrl() != null && place.getDescription() != null)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/pois/{id}", heritage.publicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.title").value(heritage.getName()))
                // 종목이 곧 뱃지다. 예전에는 contentTypeId 0 을 라벨로 되찾아 국보도 "기타" 로 나갔다(#239).
                .andExpect(jsonPath("$.data.typeLabel").value(heritage.getKind()))
                .andExpect(jsonPath("$.data.imageUrl").value(heritage.getImageUrl()))
                .andExpect(jsonPath("$.data.overview").isNotEmpty())
                .andExpect(jsonPath("$.data.address").value(heritage.getAddress()))
                // 국가유산청은 운영시간·휴무일을 주지 않는다. 없는 것을 지어내지 않는다.
                .andExpect(jsonPath("$.data.useTime").doesNotExist())
                .andExpect(jsonPath("$.data.restDate").doesNotExist());
    }

    @Test
    void 인허가_장소는_업종_분류가_뱃지로_나간다() throws Exception {
        poiDetailService.evictCache();
        // 인허가 12만 건이 통째로 "기타" 로 나가고 있었다. 지어낼 값이 없어서가 아니라 가진 값을 안 썼다.
        tourApiClient.respondDetail(() -> {
            throw new AssertionError("인허가 식별자를 TourAPI 에 물었다");
        });
        LicensedPlace place = licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, 10).getFirst();

        mockMvc.perform(get("/api/v1/pois/{id}", place.publicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeLabel").value(place.getCategory().label()))
                .andExpect(jsonPath("$.data.typeLabel").value(org.hamcrest.Matchers.not("기타")));
    }

    @Test
    void 인허가_장소는_지도_검색_링크로_넘긴다() throws Exception {
        poiDetailService.evictCache();
        // 인허가 데이터에는 영업시간·사진이 애초에 없고 다른 공식 API 로도 못 얻는다. 낡은 영업시간을
        // 우리가 보여주는 것보다 지도로 넘기는 편이 낫다.
        tourApiClient.respondDetail(() -> {
            throw new AssertionError("인허가 식별자를 TourAPI 에 물었다");
        });
        LicensedPlace place = licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, 10).getFirst();

        mockMvc.perform(get("/api/v1/pois/{id}", place.publicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapSearchUrl").value(
                        org.hamcrest.Matchers.startsWith("https://map.naver.com/p/search/")))
                .andExpect(jsonPath("$.data.food").doesNotExist());
    }

    @Test
    void 관광_API_콘텐츠에는_지도_링크를_붙이지_않는다() throws Exception {
        poiDetailService.evictCache();
        // 그쪽은 사진·소개·운영시간이 우리 응답에 이미 있다. 링크를 함께 주면 어디를 봐야 할지 갈린다.
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "완도타워", "전남 완도군", "061-1", 34.3, 126.7, "http://img/1.jpg", "전망대 소개")));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mapSearchUrl").doesNotExist());
    }

    @Test
    void 없는_국가유산이면_404_TOUR_003() throws Exception {
        poiDetailService.evictCache();
        mockMvc.perform(get("/api/v1/pois/{id}", "HER-99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR-003"));
    }

    @Test
    void 없는_장소면_404_TOUR_003() throws Exception {
        poiDetailService.evictCache();
        tourApiClient.respondDetail(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR-003"));
    }

    /**
     * 같은 장소를 다시 눌러도 외부를 다시 부르지 않는다.
     *
     * <p>운영 로그에서 같은 contentId 가 40초 안에 세 번 조회됐다. 호출이 세 배면 외부가 멈춘 순간을
     * 만날 확률도 세 배고, 일일 한도도 그만큼 탄다.
     */
    @Test
    void 같은_장소를_다시_조회하면_외부를_부르지_않는다() throws Exception {
        poiDetailService.evictCache();
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "126508", 12, "완도타워", "전남 완도군", "061-1", 34.3, 126.7, "http://img/1.jpg", "전망대 소개")));
        tourApiClient.respondIntro(Optional::empty);
        tourApiClient.resetDetailCallCount();

        mockMvc.perform(get("/api/v1/pois/{id}", "126508")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pois/{id}", "126508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("완도타워"));

        assertEquals(1, tourApiClient.detailCallCount());
    }

    /**
     * 외부가 죽었는데 <b>내려보낼 직전 값도 없으면</b> 502 로 알린다 — 조용히 빈 화면을 주지 않는다.
     *
     * <p>반대편(직전 값이 있으면 그걸 내린다)은 여기서 재현하지 않는다. TTL 이 6시간이라 만료를 기다릴
     * 수 없고, 시계를 주입하는 seam 도 두지 않았다. stale-while-error 자체는 캐시 프리미티브의 단위
     * 테스트가 짧은 TTL 로 덮는다({@code ExternalDataCacheTest}). 여기서 확인할 것은 그 정책을 이
     * 서비스가 골라 쓰는지이고, 정책이 갈리는 지점인 "직전 값 없음" 이 이 테스트다.
     */
    @Test
    void 외부가_실패했는데_직전_값도_없으면_502로_알린다() throws Exception {
        poiDetailService.evictCache();
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "777", 12, "청령포", "강원 영월군", null, 37.1, 128.4, "http://img/7.jpg", "명승 소개")));
        tourApiClient.respondIntro(Optional::empty);
        mockMvc.perform(get("/api/v1/pois/{id}", "777")).andExpect(status().isOk());

        // 캐시를 만료시키지 않고는 재조회가 안 일어나므로, 캐시를 비우고 실패하게 만든다.
        poiDetailService.evictCache();
        tourApiClient.respondDetail(() -> {
            throw TourApiException.lookupFailed(new IllegalStateException("read timeout"));
        });

        // 비운 뒤라 직전 값이 없다 — 이때는 502 가 맞다. 조용히 빈 화면을 주지 않는다.
        mockMvc.perform(get("/api/v1/pois/{id}", "777"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TOUR-001"));
    }

    /** 조회 실패를 성공으로 굳히지 않는다 — 외부가 돌아오면 다시 받아온다. */
    @Test
    void 실패한_뒤_외부가_돌아오면_다시_받아온다() throws Exception {
        poiDetailService.evictCache();
        tourApiClient.respondDetail(() -> {
            throw TourApiException.lookupFailed(new IllegalStateException("read timeout"));
        });
        mockMvc.perform(get("/api/v1/pois/{id}", "888")).andExpect(status().isBadGateway());

        poiDetailService.evictCache();
        tourApiClient.respondDetail(() -> Optional.of(new TourPoiDetail(
                "888", 12, "장릉", "강원 영월군", null, 37.1, 128.4, "http://img/8.jpg", "사적 소개")));
        tourApiClient.respondIntro(Optional::empty);

        mockMvc.perform(get("/api/v1/pois/{id}", "888"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("장릉"));
    }

    /** 우리 DB 가 답하는 식별자는 캐시를 타지 않는다 — 외부를 애초에 안 부른다. */
    @Test
    void 국가유산_식별자는_외부를_부르지_않는다() throws Exception {
        poiDetailService.evictCache();
        HeritagePlace heritage = heritagePlaceRepository.findVisitableCandidates(UISEONG, 1).getFirst();
        tourApiClient.resetDetailCallCount();

        mockMvc.perform(get("/api/v1/pois/{id}", heritage.publicId())).andExpect(status().isOk());

        assertEquals(0, tourApiClient.detailCallCount());
    }
}
