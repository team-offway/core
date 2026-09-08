package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.service.CourseGenerationService;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.CampingPlace;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.camping.GoCampingClient;
import com.offway.core.trip.infrastructure.camping.StubGoCampingClient;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsite;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.repository.CampingPlaceRepository;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.PoiDetail;
import com.offway.core.trip.service.dto.RegionPois;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 야영장이 <b>숙박 후보로 실제로 쓰이는가</b>(#510).
 *
 * <p>여기서 잠그는 것은 넷이다.
 *
 * <ol>
 *   <li>우리 89곳 것만, 휴장을 뺀 채 저장되는가
 *   <li><b>숙박이 넉넉해도 후보에 오르는가</b> — 이게 이 PR 의 핵심이다
 *   <li>같은 야영장이 두 번 뜨지 않는가
 *   <li><b>{@code CMP-} 상세가 404 가 아닌가</b> — #480 이 축제에서 빠뜨린 그 자리
 * </ol>
 */
@SpringBootTest
@Transactional
class CampingPoolIntegrationTest {

    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 9, 30);

    /** 1회차 시각. 회차를 시각으로 가르므로 테스트가 실제 시계에 기대지 않게 명시한다. */
    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 9, 8, 4, 40, 0);

    /** 2회차 — 한 달 뒤. 1회차보다 뒤여야 앞 회차 행이 정리 대상이 된다. */
    private static final LocalDateTime SECOND_RUN = FIRST_RUN.plusMonths(1);

    @Autowired
    private CampingPlaceRefreshService refreshService;

    @Autowired
    private CampingPlaceRepository campingPlaceRepository;

    @Autowired
    private RegionPoiService regionPoiService;

    @Autowired
    private PoiDetailService poiDetailService;

    @Autowired
    private CourseGenerationService courseGenerationService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private GoCampingClient goCampingClient;

    @Autowired
    private TourApiClient tourApiClient;

    @Test
    void 우리_지역_야영장만_저장한다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(
                List.of(야영장(region, "1", "우리야영장"), 야영장("서울특별시", "종로구", "2", "남의야영장")), 2));

        int saved = refreshService.refresh(FIRST_RUN).saved();

        assertEquals(1, saved, "우리 89곳 밖 야영장은 안 담는다");
        List<CampingPlace> ours = campingPlaceRepository.findCandidates(region.getId(), 10);
        assertEquals(1, ours.size());
        assertEquals("우리야영장", ours.get(0).getName());
    }

    /**
     * <b>같은 이름의 시군구를 시도로 가른다</b>(#502).
     *
     * <p>우리 89곳 안에 고성군이 둘(강원·경남), 서구가 둘(대구·부산)이다. 시군구명만 보면 한쪽에만 붙어
     * 나머지는 비고, 붙은 쪽에는 남의 야영장이 섞인다.
     */
    @Test
    void 같은_이름의_시군구는_시도로_갈라_붙인다() {
        List<Region> 동명 = 이름이겹치는두지역();
        Region 첫째 = 동명.get(0);
        Region 둘째 = 동명.get(1);
        stub().respond(() -> new GoCampsiteResult(
                List.of(야영장(첫째, "1", "첫째지역야영장"), 야영장(둘째, "2", "둘째지역야영장")), 2));

        refreshService.refresh(FIRST_RUN);

        List<CampingPlace> 첫째것 = campingPlaceRepository.findCandidates(첫째.getId(), 10);
        List<CampingPlace> 둘째것 = campingPlaceRepository.findCandidates(둘째.getId(), 10);
        assertEquals(1, 첫째것.size(), 첫째.getSido() + " " + 첫째.getSigungu() + " 에 안 붙었다");
        assertEquals(1, 둘째것.size(), 둘째.getSido() + " " + 둘째.getSigungu() + " 에 안 붙었다");
        assertEquals("첫째지역야영장", 첫째것.get(0).getName());
        assertEquals("둘째지역야영장", 둘째것.get(0).getName());
    }

    /**
     * <b>휴장한 야영장은 담지 않는다.</b> 실측 3,115건 중 123건이다.
     *
     * <p>문 닫은 곳을 코스에 넣으면 여행자가 헛걸음한다 — 카드가 비는 것보다 나쁘다.
     */
    @Test
    void 휴장한_야영장은_담지_않는다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(휴장(region, "1", "문닫은야영장")), 1));

        int saved = refreshService.refresh(FIRST_RUN).saved();

        assertEquals(0, saved);
        assertTrue(campingPlaceRepository.findCandidates(region.getId(), 10).isEmpty());
    }

    /** 좌표가 없으면 동선에 못 올린다 — 실측 3,115건 중 10건이다. */
    @Test
    void 좌표_없는_야영장은_담지_않는다() {
        Region region = 우리지역();
        GoCampsite 좌표없음 = new GoCampsite("1", "좌표없는야영장",
                region.getSido() + " " + region.getSigungu() + " 어딘가", region.getSigungu(),
                null, null, "일반야영장", null, null, null, null, null, null, null, null, true);
        stub().respond(() -> new GoCampsiteResult(List.of(좌표없음), 1));

        assertEquals(0, refreshService.refresh(FIRST_RUN).saved());
    }

    /**
     * <b>숙박이 넉넉해도 야영장이 후보에 오른다</b> — 이 PR 의 핵심이다.
     *
     * <h2>왜 이걸 잠그나</h2>
     *
     * <p>인허가·국가유산이 앉은 자리({@code supplementedWith})는 {@code MIN_STAYS}(2)에 못 미칠 때만
     * 쓰인다. 우리 숙박 풀은 지역당 평균 12건이라 <b>사실상 한 번도 참이 아니다</b> — 야영장을 거기
     * 넣으면 한 건도 안 쓰인다.
     *
     * <p>그런데 그렇게 만들어도 <b>코스는 멀쩡히 나온다.</b> 슬롯도 차고 200 이 떨어진다. 사진 없는
     * 숙소가 계속 나갈 뿐이라, 이 단언이 없으면 아무도 모른다.
     */
    @Test
    void 숙박이_넉넉해도_야영장이_후보에_오른다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "사진있는야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        // 숙박을 임계(2)보다 넉넉히 준다 — 보충 경로는 여기서 닫힌다.
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지와숙박(15, 5), 20));

        RegionPois pois = regionPoiService.collect(region.getId(), TRAVEL_DATE);

        assertTrue(pois.stays().size() > 2, "이 시나리오는 숙박이 임계를 넘어야 성립한다");
        assertTrue(pois.stays().stream().anyMatch(c -> c.contentId().startsWith("CMP-")),
                "숙박이 넉넉하면 야영장이 안 쓰인다 — 보충 자리에 넣으면 이렇게 된다");
    }

    /** 사진과 한 줄 소개가 후보까지 따라온다 — 그게 이 소스를 들여온 이유다. */
    @Test
    void 사진과_한줄소개가_후보까지_간다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "사진있는야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지와숙박(15, 5), 20));

        PoiCandidate camping = regionPoiService.collect(region.getId(), TRAVEL_DATE).stays().stream()
                .filter(c -> c.contentId().startsWith("CMP-"))
                .findFirst()
                .orElseThrow();

        assertNotNull(camping.imageUrl(), "사진이 없으면 카드가 비어 들여온 이유가 사라진다");
        assertEquals("숲에서 자는 하룻밤", camping.catchphrase());
    }

    /**
     * <b>사진 있는 야영장이 먼저 온다.</b>
     *
     * <p>상한(20건)에 걸려 잘릴 때 사진 없는 쪽이 앞에 오면 얻는 것이 사라진다.
     */
    @Test
    void 사진_있는_야영장이_앞에_온다() {
        Region region = 우리지역();
        GoCampsite 사진없음 = 사진없는야영장(region, "1", "사진없는야영장");
        GoCampsite 사진있음 = 야영장(region, "2", "사진있는야영장");
        // 사진 없는 것을 먼저 넣어도 정렬이 뒤집어야 한다.
        stub().respond(() -> new GoCampsiteResult(List.of(사진없음, 사진있음), 2));
        refreshService.refresh(FIRST_RUN);

        List<CampingPlace> found = campingPlaceRepository.findCandidates(region.getId(), 10);

        assertEquals(2, found.size(), "사진 없는 것도 버리지 않는다 — 얇은 지역에서는 그것도 후보다");
        assertEquals("사진있는야영장", found.get(0).getName());
    }

    /**
     * <b>같은 야영장이 두 번 뜨지 않는다.</b>
     *
     * <p>TourAPI 숙박에 이미 야영장이 섞여 있다 — 실측에서 375건이 겹쳤다. 그대로 두면 코스 후보에
     * 같은 곳이 두 번 오른다.
     */
    @Test
    void 이미_있는_야영장은_두_번_담기지_않는다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "겹치는야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        // TourAPI 가 같은 이름·같은 좌표로 이미 준 숙박.
        List<TourPoi> pois = new ArrayList<>(관광지와숙박(15, 4));
        pois.add(new TourPoi("C-DUP", 32, "AC", "겹치는야영장", "주소",
                CAMP_LAT, CAMP_LNG, "http://img/dup.jpg", null, null));
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(pois, pois.size()));

        List<PoiCandidate> stays = regionPoiService.collect(region.getId(), TRAVEL_DATE).stays();

        assertEquals(1, stays.stream().filter(c -> "겹치는야영장".equals(c.title())).count(),
                "같은 야영장이 두 번 올랐다 — 코스에 같은 곳이 두 번 뜬다");
    }

    /**
     * <b>{@code CMP-} 상세가 404 가 아니다</b> — #480 이 축제에서 빠뜨린 그 자리다.
     *
     * <p>후보로 실으면서 상세 분기를 안 붙이면 식별자가 관광 API 로 넘어가 <b>외부가 멀쩡할 때도
     * 404</b> 가 난다. 카드에 떠 있는 야영장을 누르면 없다고 답하는 셈이다.
     */
    @Test
    void 야영장_상세를_우리_DB_가_답한다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "상세볼야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        CampingPlace saved = campingPlaceRepository.findCandidates(region.getId(), 1).get(0);

        PoiDetail detail = poiDetailService.detail(saved.publicId());

        assertEquals("상세볼야영장", detail.title());
        assertEquals("일반야영장", detail.typeLabel(), "업종이 곧 뱃지다");
        assertNotNull(detail.imageUrl());
        assertEquals("숲에서 자는 하룻밤", detail.catchphrase());
    }

    /**
     * <b>우리 DB 출처 중 유일하게 "언제 여나" 에 답한다.</b>
     *
     * <p>인허가·국가유산·축제는 이 값이 없어 지도로 넘겼다. 야영장은 운영기간·운영일·예약방법이 오므로
     * 상세가 비지 않는다.
     */
    @Test
    void 운영_정보가_상세에_실린다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "운영정보야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        CampingPlace saved = campingPlaceRepository.findCandidates(region.getId(), 1).get(0);

        PoiDetail detail = poiDetailService.detail(saved.publicId());

        assertNotNull(detail.intro(), "운영 정보를 아는데 비웠다");
        assertEquals("봄,여름,가을,겨울 · 평일+주말", detail.intro().useTime());
        assertEquals("온라인실시간예약", detail.intro().reservation());
        assertNull(detail.mapSearchUrl(), "사진이 있으면 지도 링크가 사용자를 갈라놓는다");
    }

    /** 운영 정보를 모르면 칸 자체를 안 만든다 — 없는 것을 지어내지 않는다. */
    @Test
    void 운영_정보를_모르면_비운다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(사진없는야영장(region, "1", "정보없는야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        CampingPlace saved = campingPlaceRepository.findCandidates(region.getId(), 1).get(0);

        PoiDetail detail = poiDetailService.detail(saved.publicId());

        assertNull(detail.intro());
        assertNotNull(detail.mapSearchUrl(), "사진도 운영정보도 없으면 지도로 넘긴다");
    }

    /**
     * <b>사진 있는 숙소가 있으면 야영장을 코스에 안 올린다</b>(#510).
     *
     * <h2>왜 이걸 잠그나</h2>
     *
     * <p>숙박 슬롯은 볼거리 중심에서 <b>가장 가까운 것</b>으로 뽑는다. 야영장은 산·계곡·유적 근처라
     * 그 중심에 가까워, 거리만 보면 <b>사진 있는 호텔을 밀어낸다.</b>
     *
     * <p>실측(2026-09-08 · 83곳 × 2박)이 그 대가를 보여준다 — 거리만 보면 야영장이 숙박 자리의
     * 42%(71/166)를 차지하고 두 밤 다 캠핑인 지역이 20곳 나오며, <b>사진 있는 숙소가 141 → 119 로
     * 줄었다.</b> 야영장을 들여온 이유와 정반대다.
     *
     * <p>이 픽스처가 정확히 그 상황이다 — 야영장이 볼거리 중심에서 약 7km, 관광 API 숙소가 약 14km 다.
     * <b>거리만 보면 야영장이 반드시 뽑힌다.</b>
     */
    @Test
    void 사진_있는_숙소가_있으면_야영장이_코스에_안_올라간다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "가까운야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지와숙박(15, 5), 20));

        List<Slot> stays = courseGenerationService.generate(코스요청(region)).course().getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .filter(slot -> slot.getKind() == SlotKind.STAY)
                .toList();

        assertFalse(stays.isEmpty(), "이 시나리오는 숙박 슬롯이 있어야 성립한다");
        assertTrue(stays.stream().noneMatch(slot -> slot.getPoiContentId().startsWith("CMP-")),
                "야영장이 사진 있는 숙소를 밀어냈다 — 뽑힌 것: "
                        + stays.stream().map(Slot::getPoiContentId).toList());
    }

    /**
     * <b>숙소가 아예 없으면 야영장이라도 올린다.</b>
     *
     * <p>앞 단언만 두면 "야영장을 아예 안 쓴다" 로 고쳐도 초록이다. 그러면 이 표를 들여온 이유가
     * 통째로 사라지는데, 코스는 여전히 나오므로 아무도 모른다.
     */
    @Test
    void 숙소가_없으면_야영장을_올린다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "유일한야영장")), 1));
        refreshService.refresh(FIRST_RUN);
        // 숙박이 하나도 없는 지역 — 관광 API 가 볼거리만 준다.
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지와숙박(15, 0), 15));

        List<Slot> stays = courseGenerationService.generate(코스요청(region)).course().getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .filter(slot -> slot.getKind() == SlotKind.STAY)
                .toList();

        assertTrue(stays.stream().anyMatch(slot -> slot.getPoiContentId().startsWith("CMP-")),
                "잘 곳이 야영장뿐인데 슬롯이 비었다");
    }

    @Test
    void 없는_야영장을_물으면_404() {
        assertThrows(TourApiException.class, () -> poiDetailService.detail("CMP-99999999"));
    }

    /** 같은 야영장을 두 번 받아도 늘지 않는다 — 자연키(externalId)가 막는다. */
    @Test
    void 같은_야영장을_두_번_받아도_한_건이다() {
        Region region = 우리지역();
        stub().respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "같은야영장")), 1));

        refreshService.refresh(FIRST_RUN);
        refreshService.refresh(SECOND_RUN);

        assertEquals(1, campingPlaceRepository.findCandidates(region.getId(), 10).size());
    }

    /** 이번에 안 온 야영장은 폐업·휴장으로 보고 지운다. */
    @Test
    void 이번에_안_온_야영장은_지운다() {
        Region region = 우리지역();
        StubGoCampingClient stub = stub();
        stub.respond(() -> new GoCampsiteResult(
                List.of(야영장(region, "1", "남을야영장"), 야영장(region, "2", "사라질야영장")), 2));
        refreshService.refresh(FIRST_RUN);

        stub.respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "남을야영장")), 1));
        refreshService.refresh(SECOND_RUN);

        List<String> names = campingPlaceRepository.findCandidates(region.getId(), 10).stream()
                .map(CampingPlace::getName)
                .toList();
        assertEquals(List.of("남을야영장"), names);
    }

    /**
     * <b>조회가 깨진 회차는 아무것도 지우지 않는다.</b>
     *
     * <p>"이번에 안 온 것 = 사라진 것" 이 성립하려면 온전히 받아야 한다. 빈 결과로 정리를 돌리면
     * <b>있는 야영장을 전부 지운다</b> — 조용히, 되돌릴 수 없게.
     */
    @Test
    void 조회가_깨진_회차는_지우지_않는다() {
        Region region = 우리지역();
        StubGoCampingClient stub = stub();
        stub.respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "살아남을야영장")), 1));
        refreshService.refresh(FIRST_RUN);

        stub.respond(() -> {
            throw new IllegalStateException("외부가 죽었다");
        });
        CampingPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete());
        assertEquals(1, campingPlaceRepository.findCandidates(region.getId(), 10).size(),
                "실패한 회차가 멀쩡한 야영장을 지웠다");
    }

    /** 빈 응답도 같다 — 성공처럼 보이지만 정리를 돌리면 전부 사라진다. */
    @Test
    void 빈_응답인_회차도_지우지_않는다() {
        Region region = 우리지역();
        StubGoCampingClient stub = stub();
        stub.respond(() -> new GoCampsiteResult(List.of(야영장(region, "1", "살아남을야영장")), 1));
        refreshService.refresh(FIRST_RUN);

        stub.respond(GoCampsiteResult::empty);
        CampingPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete());
        assertEquals(1, campingPlaceRepository.findCandidates(region.getId(), 10).size());
    }

    // ── 픽스처 ────────────────────────────────────────────────────────

    /** 야영장 좌표 — 겹침 판정을 보려면 TourAPI 픽스처와 같은 값을 써야 한다. */
    private static final double CAMP_LAT = 36.5684;
    private static final double CAMP_LNG = 128.7294;

    private static GoCampsite 야영장(Region region, String externalId, String name) {
        return 야영장(region.getSido(), region.getSigungu(), externalId, name);
    }

    /**
     * 주소를 <b>지역의 시도·시군구로 만든다</b>. 시도를 박아 두면 동명 시군구 테스트가 성립하지 않는다.
     */
    private static GoCampsite 야영장(String sido, String sigungu, String externalId, String name) {
        return new GoCampsite(externalId, name, sido + " " + sigungu + " 산길 1", sigungu,
                CAMP_LAT, CAMP_LNG, "일반야영장", "http://img/camp-" + externalId + ".jpg",
                "숲에서 자는 하룻밤", "긴 소개글", "033-000-0000", "https://example.kr",
                "봄,여름,가을,겨울", "평일+주말", "온라인실시간예약", true);
    }

    private static GoCampsite 휴장(Region region, String externalId, String name) {
        GoCampsite open = 야영장(region, externalId, name);
        return new GoCampsite(open.externalId(), open.name(), open.address(), open.sigunguName(),
                open.lat(), open.lng(), open.induty(), open.imageUrl(), open.lineIntro(), open.intro(),
                open.tel(), open.homepageUrl(), open.operPeriod(), open.operDays(), open.reservation(), false);
    }

    /** 사진도 운영 정보도 없는 야영장 — 실제로 25% 가 사진이 없다. */
    private static GoCampsite 사진없는야영장(Region region, String externalId, String name) {
        return new GoCampsite(externalId, name,
                region.getSido() + " " + region.getSigungu() + " 산길 2", region.getSigungu(),
                CAMP_LAT + 0.01, CAMP_LNG + 0.01, "일반야영장", null,
                null, null, null, null, null, null, null, true);
    }

    /** TourAPI 픽스처 — 볼거리와 숙박을 섞는다. 좌표가 있어야 후보로 산다. */
    private static List<TourPoi> 관광지와숙박(int sights, int stays) {
        List<TourPoi> pois = new ArrayList<>();
        for (int i = 0; i < sights; i++) {
            pois.add(new TourPoi("C-S" + i, 12, "VE", "관광지" + i, "주소",
                    36.5 + i * 0.001, 128.7 + i * 0.001, "http://img/s" + i + ".jpg", null, null));
        }
        for (int i = 0; i < stays; i++) {
            pois.add(new TourPoi("C-A" + i, 32, "AC", "숙소" + i, "주소",
                    36.4 + i * 0.001, 128.6 + i * 0.001, "http://img/a" + i + ".jpg", null, null));
        }
        return pois;
    }

    /** 2박3일 자차 코스 — 숙박 슬롯이 생기는 가장 단순한 요청이다. */
    private static GenerateCourse 코스요청(Region region) {
        return GenerateCourse.builder()
                .regionId(region.getId())
                .travelDays(3)
                .density(Density.RELAXED)
                .transport(TransportMode.CAR)
                .originLat(36.5)
                .originLng(128.7)
                .travelDate(TRAVEL_DATE)
                .startDayLeave(StartDayLeave.FULL_DAY)
                .build();
    }

    private StubGoCampingClient stub() {
        return (StubGoCampingClient) goCampingClient;
    }

    private Region 우리지역() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0);
    }

    /** 우리 89곳 안에서 시군구명이 겹치는 두 지역 — 실측상 고성군(강원·경남)과 서구(대구·부산)다. */
    private List<Region> 이름이겹치는두지역() {
        Map<String, List<Region>> byName = new HashMap<>();
        for (Region region : regionRepository.findAll()) {
            byName.computeIfAbsent(region.getSigungu(), name -> new ArrayList<>()).add(region);
        }
        return byName.values().stream()
                .filter(regions -> regions.size() >= 2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("이름이 겹치는 지역이 없어 이 테스트가 성립하지 않는다"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        GoCampingClient stubGoCampingClient() {
            return new StubGoCampingClient();
        }

        /**
         * 숙박 수를 우리가 정하려면 TourAPI 도 잡아야 한다 — 야영장이 <b>보충 판정과 무관하게</b>
         * 오르는지를 보는 것이 목적이라, "숙박이 임계를 넘는 지역" 을 만들어야 한다.
         */
        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }
}
