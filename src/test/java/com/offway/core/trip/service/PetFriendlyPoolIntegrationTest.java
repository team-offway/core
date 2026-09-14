package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.service.PetAccompanyProvider;
import com.offway.core.leave.domain.StartDayLeave;
import com.offway.core.region.domain.Region;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.controller.dto.PetAccompanyResponse;
import com.offway.core.trip.service.dto.PetAccompany;
import java.util.Map;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.PetFriendlyPlace;
import com.offway.core.trip.infrastructure.pet.PetTourClient;
import com.offway.core.trip.infrastructure.pet.StubPetTourClient;
import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourPlace;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import com.offway.core.trip.repository.PetFriendlyPlaceRepository;
import com.offway.core.trip.domain.TourApiException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반려동반 표식이 <b>실제로 적재되는가</b>(#566).
 *
 * <p>여기서 잠그는 것은 넷이다.
 *
 * <ol>
 *   <li>우리 89곳 것만, <b>법정동 코드</b>로 정확히 갈라 저장되는가
 *   <li><b>상세를 못 받아도 표식을 잃지 않는가</b> — 칩은 뜨고 상세만 비는 것이 설계다
 *   <li>목록이 깨진 회차가 <b>기존 표식을 지우지 않는가</b>
 *   <li>반려동반을 그만둔 장소가 <b>정리되는가</b> — 안 지우면 못 들어가는 곳에 칩이 뜬다
 * </ol>
 */
@SpringBootTest
@Transactional
class PetFriendlyPoolIntegrationTest {

    /** 1회차 시각. 회차를 시각으로 가르므로 테스트가 실제 시계에 기대지 않게 명시한다. */
    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 9, 9, 5, 10, 0);

    /** 2회차 — 한 달 뒤. 1회차보다 뒤여야 앞 회차 행이 정리 대상이 된다. */
    private static final LocalDateTime SECOND_RUN = FIRST_RUN.plusMonths(1);

    @Autowired
    private PetFriendlyPlaceRefreshService refreshService;

    @Autowired
    private PetFriendlyPlaceRepository petFriendlyPlaceRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PetTourClient petTourClient;

    @Autowired
    private PetAccompanyProvider petAccompanyProvider;

    @Test
    void 우리_지역_장소만_법정동_코드로_갈라_저장한다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(
                List.of(장소(region, "1", "우리반려동반"), 남의장소("2", "남의반려동반")), 2));
        stub().respondDetail(id -> Optional.of(전구역(id)));

        int saved = refreshService.refresh(FIRST_RUN).saved();

        assertEquals(1, saved, "우리 89곳 밖 장소는 안 담는다");
        List<PetFriendlyPlace> ours = petFriendlyPlaceRepository.findByRegionId(region.getId());
        assertEquals(1, ours.size());
        assertEquals("우리반려동반", ours.get(0).getName());
        assertTrue(ours.get(0).allowsWholeArea());
    }

    /**
     * <b>같은 이름의 시군구도 코드로는 안 섞인다</b> — 주소 파싱을 쓰지 않는 이유다(#502 와 대비).
     *
     * <p>고캠핑은 주소 문자열을 읽어 시도까지 봐야 갈렸는데, 여기는 코드가 유일해 그 문제 자체가 없다.
     */
    @Test
    void 이름이_겹치는_지역도_코드로_정확히_갈린다() {
        List<Region> 동명 = 이름이겹치는두지역();
        Region 첫째 = 동명.get(0);
        Region 둘째 = 동명.get(1);
        stub().respondList(() -> new PetTourResult(
                List.of(장소(첫째, "1", "첫째지역장소"), 장소(둘째, "2", "둘째지역장소")), 2));
        stub().respondDetail(id -> Optional.of(전구역(id)));

        refreshService.refresh(FIRST_RUN);

        List<PetFriendlyPlace> 첫째것 = petFriendlyPlaceRepository.findByRegionId(첫째.getId());
        List<PetFriendlyPlace> 둘째것 = petFriendlyPlaceRepository.findByRegionId(둘째.getId());
        assertEquals(1, 첫째것.size(), 첫째.getSido() + " " + 첫째.getSigungu() + " 에 안 붙었다");
        assertEquals(1, 둘째것.size(), 둘째.getSido() + " " + 둘째.getSigungu() + " 에 안 붙었다");
        assertEquals("첫째지역장소", 첫째것.get(0).getName());
        assertEquals("둘째지역장소", 둘째것.get(0).getName());
    }

    /**
     * <b>상세를 못 받아도 표식을 남긴다.</b> 이게 이 PR 의 설계 결정이다.
     *
     * <p>442건을 도는 회차에서 한 건이 실패했다고 그 장소를 버리면 "데려갈 수 있다" 는 사실까지 잃는다.
     * 칩은 뜨고 열 내용만 빈다.
     */
    @Test
    void 상세를_못_받아도_표식은_남긴다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "상세없는장소")), 1));
        stub().respondDetail(id -> Optional.empty());

        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(FIRST_RUN);

        assertEquals(1, outcome.saved(), "상세가 없어도 저장한다");
        assertEquals(1, outcome.detailsMissing(), "못 받은 수를 세어 드러낸다");
        PetFriendlyPlace saved = petFriendlyPlaceRepository.findByRegionId(region.getId()).get(0);
        assertFalse(saved.knowsCondition(), "열 내용이 없다는 것을 화면이 알아야 한다");
        assertFalse(saved.allowsWholeArea(), "모르는 것을 전 구역 가능으로 내리지 않는다");
        assertNull(saved.getAccompanyArea());
    }

    /** 일부구역·체중 제한이 그대로 실린다 — 실측에서 절반이 이쪽이었다. */
    @Test
    void 일부구역과_체중_제한을_그대로_싣는다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "일부구역장소")), 1));
        stub().respondDetail(id -> Optional.of(new PetTourDetail(
                id, "일부구역 동반가능", "5kg 이내 소형견", "목줄 착용", "배변봉투 지참", null, null, null, null)));

        refreshService.refresh(FIRST_RUN);

        PetFriendlyPlace saved = petFriendlyPlaceRepository.findByRegionId(region.getId()).get(0);
        assertFalse(saved.allowsWholeArea(), "일부구역인데 전 구역으로 답하면 사용자가 가서 못 들어간다");
        assertEquals("일부구역 동반가능", saved.getAccompanyArea());
        assertEquals("5kg 이내 소형견", saved.getAccompanyPet());
        assertTrue(saved.knowsCondition());
        assertFalse(saved.knowsFacilities(), "시설 정보는 대개 비어 온다");
    }

    /**
     * <b>목록이 깨진 회차는 기존 표식을 지우지 않는다.</b>
     *
     * <p>조회가 실패하면 "이번에 안 온 것 = 사라진 것" 이 성립하지 않는다. 그때 지우면 멀쩡한 표식을
     * 우리가 없앤다.
     */
    @Test
    void 목록_조회가_실패하면_기존_표식을_지우지_않는다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "기존장소")), 1));
        stub().respondDetail(id -> Optional.of(전구역(id)));
        refreshService.refresh(FIRST_RUN);
        assertEquals(1, petFriendlyPlaceRepository.findByRegionId(region.getId()).size());

        stub().respondList(() -> {
            throw TourApiException.petTourLookupFailed(new IllegalStateException("외부 장애"));
        });
        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete(), "온전하지 않은 회차다");
        assertEquals(1, petFriendlyPlaceRepository.findByRegionId(region.getId()).size(),
                "실패한 회차가 멀쩡한 표식을 지웠다");
    }

    /** 빈 목록으로도 정리하지 않는다 — 그러면 있는 표식을 전부 지운다. */
    @Test
    void 받은_장소가_없으면_정리를_건너뛴다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "기존장소")), 1));
        stub().respondDetail(id -> Optional.of(전구역(id)));
        refreshService.refresh(FIRST_RUN);

        stub().respondList(PetTourResult::empty);
        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete());
        assertEquals(1, petFriendlyPlaceRepository.findByRegionId(region.getId()).size());
    }

    /**
     * <b>반려동반을 그만둔 장소는 정리한다.</b>
     *
     * <p>안 지우면 이제 데려갈 수 없는 곳에 칩이 뜬다 — 사용자가 반려동물을 데리고 갔다가 못 들어간다.
     */
    @Test
    void 이번_회차에_안_온_장소는_정리한다() {
        Region region = 우리지역();
        stub().respondDetail(id -> Optional.of(전구역(id)));
        stub().respondList(() -> new PetTourResult(
                List.of(장소(region, "1", "계속되는곳"), 장소(region, "2", "그만둔곳")), 2));
        refreshService.refresh(FIRST_RUN);
        assertEquals(2, petFriendlyPlaceRepository.findByRegionId(region.getId()).size());

        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "계속되는곳")), 1));
        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertTrue(outcome.complete());
        List<PetFriendlyPlace> remaining = petFriendlyPlaceRepository.findByRegionId(region.getId());
        assertEquals(1, remaining.size(), "그만둔 곳이 남아 있으면 못 들어가는 데 칩이 뜬다");
        assertEquals("계속되는곳", remaining.get(0).getName());
    }

    /**
     * <b>상세가 다 끝나지 못한 회차도 정리를 건너뛴다.</b>
     *
     * <p>목록이 온전해도 상세 팬아웃이 상한에 걸리면 끝나지 못한 장소가 저장 대상에서 빠진다. 그
     * 상태로 정리를 돌리면 "이번에 안 온 것" 에 <b>아직 처리 중이던 멀쩡한 장소</b>가 섞여 표식을
     * 우리 손으로 지운다 — 목록이 깨졌을 때와 결과가 같다.
     *
     * <p>상세 조회가 {@code Optional.empty()} 로 실패하는 것과는 다르다. 그건 unknown 으로 한 건이
     * 채워져 회차가 온전하고(칩은 뜨고 열 내용만 빈다), 여기는 채워지지 않는 쪽이다.
     */
    @Test
    void 상세가_다_끝나지_못하면_정리를_건너뛴다() {
        Region region = 우리지역();
        stub().respondDetail(id -> Optional.of(전구역(id)));
        stub().respondList(() -> new PetTourResult(
                List.of(장소(region, "1", "먼저끝난곳"), 장소(region, "2", "못끝낸곳")), 2));
        refreshService.refresh(FIRST_RUN);
        assertEquals(2, petFriendlyPlaceRepository.findByRegionId(region.getId()).size());

        // 2번만 조립을 못 끝낸다 — 예외로 끝난 작업은 collected 에 아무것도 넣지 못한다.
        stub().respondDetail(id -> {
            if ("2".equals(id)) {
                throw new IllegalStateException("상세 조립이 끝나지 못했다");
            }
            return Optional.of(전구역(id));
        });
        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete(), "부분만 끝난 회차를 완료로 보면 정리가 돈다");
        assertEquals(2, petFriendlyPlaceRepository.findByRegionId(region.getId()).size(),
                "못 끝낸 장소의 표식이 남아 있어야 한다");
    }

    /** 같은 장소가 두 번 들어오지 않는다 — 자연키가 콘텐츠 ID 다. */
    @Test
    void 같은_장소를_두_번_저장하지_않는다() {
        Region region = 우리지역();
        stub().respondDetail(id -> Optional.of(전구역(id)));
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "같은곳")), 1));

        refreshService.refresh(FIRST_RUN);
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "이름이바뀐같은곳")), 1));
        refreshService.refresh(SECOND_RUN);

        List<PetFriendlyPlace> ours = petFriendlyPlaceRepository.findByRegionId(region.getId());
        assertEquals(1, ours.size(), "자연키가 겹치면 덮어야 한다");
        assertEquals("이름이바뀐같은곳", ours.get(0).getName(), "최신 값으로 덮는다");
    }

    /**
     * <b>우리 89곳에 하나도 안 붙으면 정리하지 않는다.</b>
     *
     * <p>전국을 받았는데 매칭이 0건이면 법정동 코드 체계가 바뀐 것이다. 그 회차로 정리를 돌리면
     * 표식을 전부 잃는다.
     */
    @Test
    void 우리_지역에_하나도_안_붙으면_회차를_건너뛴다() {
        Region region = 우리지역();
        stub().respondDetail(id -> Optional.of(전구역(id)));
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "1", "기존장소")), 1));
        refreshService.refresh(FIRST_RUN);

        stub().respondList(() -> new PetTourResult(List.of(남의장소("2", "남의것만")), 1));
        PetFriendlyPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(SECOND_RUN);

        assertFalse(outcome.complete());
        assertEquals(1, petFriendlyPlaceRepository.findByRegionId(region.getId()).size());
    }

    /**
     * <b>적재한 표식이 코스 슬롯에 실제로 붙는가</b> — 이것이 사용자가 보는 결과다.
     *
     * <p>함께 잠그는 것: 판정할 수 없는 출처(인허가 {@code LIC-})는 <b>키가 없다</b>. 그 자리에
     * "반려동물 안 됨" 을 내리면 사용자가 갈 수 있는 곳을 포기한다.
     */
    @Test
    void 코스_슬롯에_반려동반_표식이_붙고_모르는_출처는_비운다() {
        Region region = 우리지역();
        stub().respondList(() -> new PetTourResult(List.of(장소(region, "127311", "갈음이해수욕장")), 1));
        stub().respondDetail(id -> Optional.of(new PetTourDetail(
                id, "일부구역 동반가능", "5kg 이내 소형견", "목줄 착용", "배변봉투 지참", null, null, null, null)));
        refreshService.refresh(FIRST_RUN);

        Course course = 코스(region, "127311", "LIC-9");
        Map<String, PetAccompany> byContentId = petAccompanyProvider.forCourse(course);

        assertEquals(1, byContentId.size(), "반려동반인 슬롯만 키가 있다");
        PetAccompany 붙은것 = byContentId.get("127311");
        assertFalse(붙은것.wholeArea(), "일부구역인데 전 구역으로 답하면 사용자가 가서 못 들어간다");
        assertEquals("5kg 이내 소형견", 붙은것.pet());
        assertNull(byContentId.get("LIC-9"), "판정할 수 없는 출처는 키가 없다 — 불가로 내리지 않는다");

        // 응답 조각까지 이어지는지 — 이 값이 있으면 화면이 칩을 띄운다.
        PetAccompanyResponse 응답 = PetAccompanyResponse.from(붙은것);
        assertNotNull(응답);
        assertFalse(응답.wholeArea());
        assertNull(PetAccompanyResponse.from(null), "반려동반이 아니면 칩을 띄우지 않는다");
    }

    /** 반려동반 장소가 없는 지역이면 질의 결과가 비고, 화면은 칩을 하나도 띄우지 않는다. */
    @Test
    void 적재된_것이_없으면_표식이_비어_있다() {
        Region region = 우리지역();

        Map<String, PetAccompany> byContentId = petAccompanyProvider.forCourse(코스(region, "127311", "LIC-9"));

        assertTrue(byContentId.isEmpty());
    }

    /** 슬롯 두 칸 — 하나는 관광 콘텐츠 ID, 하나는 인허가라 판정 불가. */
    private static Course 코스(Region region, String tourContentId, String licensedId) {
        List<Slot> slots = List.of(
                Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, tourContentId, "관광슬롯", 36.35, 128.69, 0),
                Slot.of(2, TimeOfDay.LUNCH, SlotKind.FOOD, licensedId, "인허가슬롯", 36.36, 128.70, 10));
        return Course.sharedOnly(
                region.getId(),
                Density.RELAXED,
                TransportMode.CAR,
                List.of(DaySchedule.of(1, slots)),
                FIRST_RUN.toLocalDate().plusDays(20),
                1,
                null,
                StartDayLeave.FULL_DAY,
                null);
    }

    private StubPetTourClient stub() {
        return (StubPetTourClient) petTourClient;
    }

    private static PetTourPlace 장소(Region region, String contentId, String title) {
        return new PetTourPlace(contentId, title, region.getLegalCode());
    }

    /** 우리 89곳 밖 — 서울 종로구의 법정동 코드다. */
    private static PetTourPlace 남의장소(String contentId, String title) {
        return new PetTourPlace(contentId, title, "11110");
    }

    private static PetTourDetail 전구역(String contentId) {
        return new PetTourDetail(
                contentId, "전구역 동반가능", "전 견종 동반 가능", "목줄 착용", null, null, null, null, null);
    }

    private Region 우리지역() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.stream()
                .filter(region -> region.getLegalCode() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("법정동 코드를 가진 지역이 없어 이 테스트가 성립하지 않는다"));
    }

    /** 우리 89곳 안에서 시군구명이 겹치는 두 지역 — 실측상 고성군(강원·경남)과 서구(대구·부산)다. */
    private List<Region> 이름이겹치는두지역() {
        java.util.Map<String, List<Region>> byName = new java.util.HashMap<>();
        for (Region region : regionRepository.findAll()) {
            if (region.getLegalCode() != null) {
                byName.computeIfAbsent(region.getSigungu(), name -> new ArrayList<>()).add(region);
            }
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
        PetTourClient stubPetTourClient() {
            return new StubPetTourClient();
        }
    }
}
