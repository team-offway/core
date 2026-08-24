package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.domain.RegionPoi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 코스 후보를 <b>어느 풀에 넣는가</b>(#304).
 *
 * <p><b>야영장이 볼거리로 새고 있었다.</b> 실측(2026-08-21 · 89곳 전수)에서 대분류 {@code AC}(숙박)
 * 977건 중 <b>625건이 {@code contentTypeId=28}</b>(레포츠)로 왔다 — 전부 야영장·캠핑장·펜션이다.
 * 풀을 타입으로 가르던 시절에는 그것이 볼거리에 들어가고, 숙박 조회({@code contentTypeId=32})에는
 * 안 잡혀 <b>숙박 풀이 굶었다.</b>
 *
 * <p>{@code RegionPoiService} javadoc 이 "TourAPI 숙박은 관광사업체 위주라 지방 숙소가 거의 없다 —
 * 의성군이 1건" 이라고 적어 둔 그 증상의 원인이다. 그래서 사진 없는 인허가로 메우고 있었는데,
 * 정작 사진 있는 캠핑장이 옆에서 새고 있었다.
 */
@SpringBootTest
class RegionPoiPoolIntegrationTest {

    @Autowired
    private RegionPoiService regionPoiService;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private com.offway.core.trip.repository.RegionPoiRepository regionPoiRepository;

    /**
     * 대분류가 숙박이면 타입이 레포츠여도 <b>숙박 풀</b>이다.
     *
     * <p>이 한 건이 이 클래스의 이유다. 타입으로 가르면 볼거리로 떨어진다.
     */
    @Test
    void 야영장은_타입이_레포츠여도_숙박_풀에_들어간다() {
        tourApiClient.respond(() -> result(
                // AC05(야영장) — 실제 응답이 이 모양으로 온다
                poi("CAMP-1", 28, "AC", "느티담길캠핑장"),
                poi("SIGHT-1", 12, "NA", "가리왕산")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(has(pois.stays(), "CAMP-1"), "야영장이 숙박 풀에 없다");
        assertFalse(has(pois.sights(), "CAMP-1"), "야영장이 볼거리 풀로 샜다");
        assertTrue(has(pois.sights(), "SIGHT-1"), "볼거리가 빠졌다");
    }

    /** 대분류가 음식이면 맛집 풀이다 — 숙박과 같은 규칙이다. */
    @Test
    void 대분류가_음식이면_맛집_풀에_들어간다() {
        tourApiClient.respond(() -> result(poi("FOOD-1", 39, "FD", "밀면집")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(has(pois.foods(), "FOOD-1"), "맛집이 맛집 풀에 없다");
        assertFalse(has(pois.sights(), "FOOD-1"), "맛집이 볼거리 풀로 샜다");
    }

    /**
     * 같은 장소가 두 조회에 걸려도 풀에는 한 번만 들어간다.
     *
     * <p>전체타입 조회와 타입별 조회가 같은 숙소를 함께 물고 온다. 접지 않으면 같은 곳이 코스에
     * 두 번 들어갈 수 있다.
     */
    @Test
    void 전체타입과_타입별_조회가_겹쳐도_한_번만_담는다() {
        tourApiClient.respond(() -> result(poi("HOTEL-1", 32, "AC", "라메르호텔")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(count(pois.stays(), "HOTEL-1") == 1, "같은 숙소가 두 번 담겼다");
    }

    /**
     * 대분류가 없는 후보(우리 DB 출처)는 예전처럼 타입으로 가른다.
     *
     * <p>국가유산·인허가는 TourAPI 분류체계 밖이라 대분류가 {@code null} 이다. 그 값을 숙박·맛집이
     * 아니라는 이유로 볼거리에 넣으면 인허가 숙소가 볼거리가 된다.
     */
    @Test
    void 대분류가_없으면_타입으로_가른다() {
        tourApiClient.respond(() -> result(poi("NOLCLS-1", 12, null, "대분류 없는 관광지")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(has(pois.sights(), "NOLCLS-1"), "대분류 없는 관광지가 볼거리에 없다");
    }

    /**
     * <b>리조트는 대분류가 문화관광이어도 숙박 풀이다.</b>
     *
     * <p>실측에서 카라반·글램핑 리조트 39건이 대분류 {@code VE}(문화관광)로 왔다. 대분류만 보면 볼거리에
     * 남는데 사용자는 거기서 잔다 — 중분류 {@code VE05}(복합관광시설)를 봐야 갈린다.
     */
    @Test
    void 리조트는_대분류가_문화관광이어도_숙박_풀에_들어간다() {
        tourApiClient.respond(() -> result(poi("RESORT-1", 12, "VE", "VE05", "스카이랜드카라반 리조트")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(has(pois.stays(), "RESORT-1"), "리조트가 숙박 풀에 없다");
        assertFalse(has(pois.sights(), "RESORT-1"), "리조트가 볼거리 풀로 샜다");
    }

    /**
     * <b>전통시장은 코스 후보에 들어간다.</b>
     *
     * <p>예전에는 타입으로 갈라 쇼핑(38)을 통째로 제외했다. 실측에서 전 지역 153건이 그렇게 빠졌는데
     * 사진 보유율은 95% 였고, 대부분 전통시장·특산품점이라 여행에서 갈 만한 곳이다.
     */
    @Test
    void 쇼핑은_볼거리_풀에_들어간다() {
        tourApiClient.respond(() -> result(poi("SHOP-1", 38, "SH", "SH06", "부산진시장")));

        RegionPois pois = regionPoiService.collect(anyRegionId());

        assertTrue(has(pois.sights(), "SHOP-1"), "전통시장이 코스 후보에서 빠졌다");
    }

    private long anyRegionId() {
        List<Region> regions = regionRepository.findAll();
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0).getId();
    }

    private static boolean has(List<PoiCandidate> pool, String contentId) {
        return count(pool, contentId) > 0;
    }

    private static long count(List<PoiCandidate> pool, String contentId) {
        return pool.stream().filter(c -> contentId.equals(c.contentId())).count();
    }

    private static TourPoiResult result(TourPoi... items) {
        return new TourPoiResult(List.of(items), items.length);
    }

    private static TourPoi poi(String contentId, int typeId, String lclsSystm1, String title) {
        return poi(contentId, typeId, lclsSystm1, null, title);
    }

    private static TourPoi poi(String contentId, int typeId, String lcls1, String lcls2, String title) {
        return new TourPoi(contentId, typeId, lcls1, title, "주소", 37.5, 127.0, "http://img.jpg", "051-000-0000", lcls2);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    // ── 홈 카드용 조회(#305) — 네이티브 SQL·윈도우 함수라 실제 MySQL 로만 검증된다

    /**
     * <b>칩마다 앞의 몇 건씩</b> 고른다.
     *
     * <p>지역당 상위 N 으로 자르면 등록 수가 많은 칩이 자리를 다 차지해, "숙박" 을 눌렀을 때 빈 목록이
     * 뜬다. 칩이 뜻을 가지려면 칩마다 후보가 있어야 한다.
     */
    @Test
    void 카드_조회는_칩마다_앞의_몇_건씩_고른다() {
        long regionId = anyRegionId();
        regionPoiRepository.replaceRegion(regionId, List.of(
                cardPoi(regionId, "fc-food-1", Category.FOOD),
                cardPoi(regionId, "fc-food-2", Category.FOOD),
                cardPoi(regionId, "fc-food-3", Category.FOOD),
                cardPoi(regionId, "fc-stay-1", Category.STAY)));

        List<String> found = regionPoiRepository.findForCards(List.of(regionId), 2).stream()
                .map(RegionPoi::getContentId)
                .toList();

        assertEquals(2, found.stream().filter(id -> id.startsWith("fc-food")).count(), "맛집이 2건을 넘었다");
        assertTrue(found.contains("fc-stay-1"), "숙박이 빠졌다 — 칩마다 고르지 않았다");
    }

    /** 사진 없는 장소는 빠진다 — 섞이면 가로 목록에 회색 판이 낀다. */
    @Test
    void 카드_조회는_사진_없는_장소를_뺀다() {
        long regionId = anyRegionId();
        regionPoiRepository.replaceRegion(regionId, List.of(
                cardPoi(regionId, "fc-photo", Category.SIGHT, "http://img/a.jpg"),
                cardPoi(regionId, "fc-nophoto", Category.SIGHT, null),
                cardPoi(regionId, "fc-blank", Category.SIGHT, "")));

        List<String> found = regionPoiRepository.findForCards(List.of(regionId), 5).stream()
                .map(RegionPoi::getContentId)
                .toList();

        assertTrue(found.contains("fc-photo"));
        assertFalse(found.contains("fc-nophoto"), "사진 없는 장소가 카드에 들었다");
        assertFalse(found.contains("fc-blank"), "사진이 빈 문자열인 장소가 카드에 들었다");
    }

    /** 지역이 없으면 빈 목록 — 질의를 부르지 않는다. */
    @Test
    void 지역이_없으면_카드도_없다() {
        assertTrue(regionPoiRepository.findForCards(List.of(), 2).isEmpty());
    }

    private static RegionPoi cardPoi(long regionId, String contentId, Category category) {
        return cardPoi(regionId, contentId, category, "http://img/" + contentId + ".jpg");
    }

    private static RegionPoi cardPoi(long regionId, String contentId, Category category, String imageUrl) {
        return RegionPoi.builder()
                .regionId(regionId)
                .contentId(contentId)
                .contentTypeId(category == Category.FOOD ? 39 : 12)
                .category(category)
                .title("장소 " + contentId)
                .imageUrl(imageUrl)
                .baseYm(java.time.YearMonth.now())
                .fetchedAt(java.time.LocalDateTime.now())
                .build();
    }
}
