package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.GalleryPhoto;
import com.offway.core.trip.domain.HubAttraction;
import com.offway.core.trip.repository.GalleryPhotoRepository;
import com.offway.core.trip.repository.HubAttractionRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지역 대표 사진 선정(#196) — 중심 관광지 순위 × 관광사진 갤러리.
 *
 * <p>여기서 지키는 것은 <b>"카드에 무엇이 걸리는가"</b> 다. 89곳 전수 실측에서 1위가 터미널·역·골프장인
 * 지역이 흔했고, 그걸 그대로 쓰면 여행을 권하는 자리에 버스터미널 사진이 걸린다.
 */
@SpringBootTest
@Transactional
class RegionHeroPhotoIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 0, 0);
    private static final YearMonth BASE = YearMonth.of(2026, 7);

    @Autowired
    private RegionHeroPhotoProvider provider;

    @Autowired
    private GalleryPhotoRepository galleryPhotoRepository;

    @Autowired
    private HubAttractionRepository hubAttractionRepository;

    @Autowired
    private RegionRepository regionRepository;

    private Long anyRegionId() {
        return regionRepository.findAll().getFirst().getId();
    }

    private static HubAttraction hub(Long regionId, int rank, String name, String large) {
        return HubAttraction.builder()
                .regionId(regionId)
                .baseMonth(BASE)
                .hubRank(rank)
                .hubCode("code-" + rank)
                .name(name)
                .categoryLarge(large)
                .categoryMedium("문화관광")
                .lat(36.4)
                .lng(127.1)
                .build();
    }

    private static GalleryPhoto photo(Long regionId, String title, String url, String month) {
        return GalleryPhoto.builder()
                .galContentId(title + month)
                .title(title)
                .imageUrl(url)
                .photographyMonth(month)
                .photographyLocation("충청남도 공주시")
                .regionId(regionId)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void 순위대로_훑어_사진이_있는_첫_관광지를_고른다() {
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(
                hub(regionId, 1, "국립공주박물관", "관광지"),
                hub(regionId, 2, "공산성", "관광지")));
        // 1위는 갤러리에 없고 2위만 있다 — 사진이 있는 첫 항목이 대표가 된다.
        galleryPhotoRepository.replaceAll(List.of(photo(regionId, "공산성", "http://img/gongsan.jpg", "202405")));

        Map<Long, String> urls = provider.heroPhotoUrls(List.of(regionId), null);

        assertEquals("http://img/gongsan.jpg", urls.get(regionId));
    }

    @Test
    void 숙박은_사진이_있어도_대표가_되지_않는다() {
        // 정선군 1위는 콘도, 2위는 카지노다 — 데이터는 맞지만 카드에 걸 그림이 아니다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(
                hub(regionId, 1, "힐콘도", "숙박"),
                hub(regionId, 2, "병방치스카이워크", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(
                photo(regionId, "힐콘도", "http://img/condo.jpg", "202405"),
                photo(regionId, "병방치스카이워크", "http://img/skywalk.jpg", "202405")));

        assertEquals("http://img/skywalk.jpg", provider.heroPhotoUrls(List.of(regionId), null).get(regionId));
    }

    @Test
    void 역과_터미널은_관광지로_분류돼도_대표가_되지_않는다() {
        // 실측 — 부산 동구 1위 부산역, 대구 남구 1위 서부시외버스터미널. 둘 다 대분류가 "관광지" 다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(
                hub(regionId, 1, "부산역", "관광지"),
                hub(regionId, 2, "서부시외버스터미널", "관광지"),
                hub(regionId, 3, "부산항", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(
                photo(regionId, "부산역", "http://img/station.jpg", "202405"),
                photo(regionId, "부산항", "http://img/port.jpg", "202405")));

        assertEquals("http://img/port.jpg", provider.heroPhotoUrls(List.of(regionId), null).get(regionId));
    }

    @Test
    void 골프장_리조트는_대표가_되지_않는다() {
        // 장수군 1위가 장수골프리조트다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(
                hub(regionId, 1, "장수골프리조트", "관광지"),
                hub(regionId, 2, "방화동자연휴양림", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(
                photo(regionId, "장수골프리조트", "http://img/golf.jpg", "202405"),
                photo(regionId, "방화동자연휴양림", "http://img/forest.jpg", "202405")));

        assertEquals("http://img/forest.jpg", provider.heroPhotoUrls(List.of(regionId), null).get(regionId));
    }

    @Test
    void 여행월이_있으면_촬영월이_가까운_사진을_고른다() {
        // 10월 여행에 설경을 내보내지 않는다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(hub(regionId, 1, "공산성", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(
                photo(regionId, "공산성 설경", "http://img/winter.jpg", "202401"),
                photo(regionId, "공산성 단풍", "http://img/autumn.jpg", "202310")));

        assertEquals("http://img/autumn.jpg",
                provider.heroPhotoUrls(List.of(regionId), YearMonth.of(2026, 10)).get(regionId));
    }

    @Test
    void 십이월과_일월은_한_달_차이로_본다() {
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(hub(regionId, 1, "공산성", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(
                photo(regionId, "공산성 여름", "http://img/summer.jpg", "202407"),
                photo(regionId, "공산성 겨울", "http://img/winter.jpg", "202412")));

        assertEquals("http://img/winter.jpg",
                provider.heroPhotoUrls(List.of(regionId), YearMonth.of(2027, 1)).get(regionId));
    }

    @Test
    void 제목에_없어도_키워드에_있으면_잇는다() {
        // 실측 — 제목이 "금강철교" 인 사진의 키워드에 "공산성" 이 들어 있다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(hub(regionId, 1, "공산성", "관광지")));
        galleryPhotoRepository.replaceAll(List.of(GalleryPhoto.builder()
                .galContentId("c1")
                .title("금강철교")
                .imageUrl("http://img/bridge.jpg")
                .searchKeyword("금강철교, 국가등록문화유산, 야경, 공산성")
                .regionId(regionId)
                .updatedAt(NOW)
                .build()));

        assertEquals("http://img/bridge.jpg", provider.heroPhotoUrls(List.of(regionId), null).get(regionId));
    }

    @Test
    void 갤러리에_없으면_비워서_폴백에_넘긴다() {
        // 장수군·의성군·의령군이 이 경우다. 지어내지 않고 호출자가 TourAPI 로 내려간다.
        Long regionId = anyRegionId();
        hubAttractionRepository.replaceRegion(regionId, List.of(hub(regionId, 1, "이름없는곳", "관광지")));
        galleryPhotoRepository.replaceAll(List.of());

        assertTrue(provider.heroPhotoUrls(List.of(regionId), null).isEmpty());
    }
}
