package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.RelatedAttraction;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.repository.RelatedAttractionRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * "함께 가는 순서" 가 코스 후보 식별자로 나오는가(#186).
 *
 * <p>여기서 잠그는 것은 <b>순서와 폴백</b>이다. 연관 데이터가 없는 지역에서 빈 목록이 나와야
 * 코스가 좌표 군집으로 되돌아가고, 있으면 순위대로 나와야 "왜 이 조합인가" 에 답이 된다.
 */
@SpringBootTest
@Transactional
class RelatedAttractionQueryIntegrationTest {

    private static final YearMonth BASE = YearMonth.of(2099, 1);

    @Autowired
    private RelatedAttractionQuery relatedAttractionQuery;

    @Autowired
    private RelatedAttractionRepository relatedAttractionRepository;

    @Autowired
    private LicensedPlaceRepository licensedPlaceRepository;

    @Autowired
    private RegionRepository regionRepository;

    /**
     * <b>연관 데이터가 없으면 빈 목록이다.</b> 이게 있어야 코스가 좌표 군집으로 되돌아간다 —
     * 여기서 예외가 나거나 엉뚱한 값이 나오면 그 지역 코스가 통째로 막힌다.
     */
    @Test
    void 연관_데이터가_없는_지역은_빈_목록이다() {
        assertTrue(relatedAttractionQuery.sightPlaceIds(어느지역().getId()).isEmpty());
    }

    /** 순위가 낮은 것부터 — 그 순서가 곧 "함께 가는 정도" 다. */
    @Test
    void 함께_가는_순서대로_준다() {
        Region region = 어느지역();
        List<LicensedPlace> places = 인허가장소(region, 3);
        relatedAttractionRepository.replaceRegion(region.getId(), BASE, List.of(
                연관(region, "갑사", places.get(2), 3, "관광지"),
                연관(region, "갑사", places.get(0), 1, "관광지"),
                연관(region, "갑사", places.get(1), 2, "관광지")));

        List<String> ids = relatedAttractionQuery.sightPlaceIds(region.getId());

        assertEquals(List.of(
                LicensedPlace.publicId(places.get(0).getId()),
                LicensedPlace.publicId(places.get(1).getId()),
                LicensedPlace.publicId(places.get(2).getId())), ids);
    }

    /** 분류가 갈린다 — 볼거리 자리에 식당이 들어가면 안 된다. */
    @Test
    void 볼거리와_음식을_갈라_준다() {
        Region region = 어느지역();
        List<LicensedPlace> places = 인허가장소(region, 2);
        relatedAttractionRepository.replaceRegion(region.getId(), BASE, List.of(
                연관(region, "갑사", places.get(0), 1, "관광지"),
                연관(region, "갑사", places.get(1), 1, "음식")));

        assertEquals(List.of(LicensedPlace.publicId(places.get(0).getId())),
                relatedAttractionQuery.sightPlaceIds(region.getId()));
        assertEquals(List.of(LicensedPlace.publicId(places.get(1).getId())),
                relatedAttractionQuery.foodPlaceIds(region.getId()));
    }

    /**
     * 같은 장소가 여러 중심에 걸릴 수 있다 — <b>가장 높은 순위 한 번만</b> 남긴다.
     *
     * <p>안 접으면 같은 곳이 코스에 두 번 들어간다.
     */
    @Test
    void 여러_중심에_걸린_장소는_한_번만_나온다() {
        Region region = 어느지역();
        List<LicensedPlace> places = 인허가장소(region, 1);
        relatedAttractionRepository.replaceRegion(region.getId(), BASE, List.of(
                연관(region, "갑사", places.get(0), 5, "관광지"),
                연관(region, "마곡사", places.get(0), 1, "관광지")));

        List<String> ids = relatedAttractionQuery.sightPlaceIds(region.getId());

        assertEquals(1, ids.size(), "같은 장소가 두 번 들어가면 코스에 중복이 생긴다: " + ids);
    }

    /**
     * <b>같은 입력이면 같은 순서다.</b> 중심이 여럿이면 순위가 겹치는데(갑사 1위와 마곡사 1위),
     * 그때 순서가 흔들리면 같은 요청이 다른 코스를 낸다.
     */
    @Test
    void 순위가_겹쳐도_순서가_흔들리지_않는다() {
        Region region = 어느지역();
        List<LicensedPlace> places = 인허가장소(region, 2);
        relatedAttractionRepository.replaceRegion(region.getId(), BASE, List.of(
                연관(region, "마곡사", places.get(1), 1, "관광지"),
                연관(region, "갑사", places.get(0), 1, "관광지")));

        List<String> first = relatedAttractionQuery.sightPlaceIds(region.getId());
        List<String> second = relatedAttractionQuery.sightPlaceIds(region.getId());

        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    private static RelatedAttraction 연관(
            Region region, String hubName, LicensedPlace place, int rank, String category) {
        return RelatedAttraction.builder()
                .regionId(region.getId())
                .baseMonth(BASE)
                .hubCode("HUB-" + hubName)
                .hubName(hubName)
                .relatedCode("RLT-" + place.getId())
                .relatedName(place.getName())
                .relatedRank(rank)
                .categoryLarge(category)
                .licensedPlaceId(place.getId())
                .lat(place.getLat())
                .lng(place.getLng())
                .build();
    }

    /** 그 지역에 실제로 있는 인허가 장소 — 연관 데이터가 가리킬 대상이다. */
    private List<LicensedPlace> 인허가장소(Region region, int count) {
        List<LicensedPlace> places = new ArrayList<>(
                licensedPlaceRepository.findAllInRegion(region.getId()));
        assertTrue(places.size() >= count,
                "이 테스트는 그 지역에 인허가 장소가 " + count + "곳 이상이어야 성립한다");
        return places.subList(0, count);
    }

    /** 인허가 장소가 넉넉한 지역이라야 연관 데이터를 붙일 수 있다. */
    private Region 어느지역() {
        return regionRepository.findAll().stream()
                .filter(r -> licensedPlaceRepository.findAllInRegion(r.getId()).size() >= 3)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("인허가 장소가 3곳 이상인 지역이 없어 성립하지 않는다"));
    }
}
