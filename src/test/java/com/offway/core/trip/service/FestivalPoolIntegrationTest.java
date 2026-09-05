package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.FestivalPlace;
import com.offway.core.trip.infrastructure.festival.FestivalStandardClient;
import com.offway.core.trip.infrastructure.festival.StubFestivalStandardClient;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.repository.FestivalPlaceRepository;
import com.offway.core.trip.service.dto.PoiCandidate;
import com.offway.core.trip.service.dto.RegionPois;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표준데이터 축제가 <b>우리 지역에 붙어 저장되는가</b>(#433).
 *
 * <p>전국 1,305건이 오고 우리는 89곳만 쓴다. 여기서 잠그는 것은 셋이다 — 우리 지역만 남는가,
 * 코스에 못 올릴 것(좌표·기간 없음)이 걸러지는가, 그리고 <b>온전하지 않은 회차가 멀쩡한 축제를
 * 지우지 않는가</b>.
 *
 * <p>마지막 것이 가장 중요하다. 한 페이지만 깨져도 "이번에 안 온 것 = 취소됨" 이 성립하지 않는데,
 * 그때 지우면 되돌릴 수 없다.
 */
@SpringBootTest
@Transactional
class FestivalPoolIntegrationTest {

    private static final LocalDate DURING = LocalDate.of(2026, 9, 30);

    /** 1회차 시각. 회차를 시각으로 가르므로 테스트가 실제 시계에 기대지 않게 명시한다. */
    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 9, 6, 4, 50, 0);

    /** 2회차 — 한 달 뒤. 1회차보다 뒤여야 앞 회차 행이 정리 대상이 된다. */
    private static final LocalDateTime SECOND_RUN = FIRST_RUN.plusMonths(1);

    @Autowired
    private FestivalPlaceRefreshService refreshService;

    @Autowired
    private FestivalPlaceRepository festivalPlaceRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private FestivalStandardClient festivalStandardClient;

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private RegionPoiService regionPoiService;

    @Autowired
    private com.offway.core.trip.repository.HeritagePlaceRepository heritagePlaceRepository;

    @Test
    void 우리_지역_축제만_저장한다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(
                        List.of(축제(region.getSigungu(), "우리축제"), 축제("서울특별시종로구", "남의축제")), 2)
                : StandardFestivalResult.empty());

        int saved = refreshService.refresh(FIRST_RUN).saved();

        assertEquals(1, saved, "우리 89곳 밖 축제는 안 담는다");
        List<FestivalPlace> open = festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10);
        assertEquals(1, open.size());
        assertEquals("우리축제", open.get(0).getName());
    }

    /** 좌표 없는 것이 446건 중 101건이다. 동선에 못 올리므로 후보에서 뺀다. */
    @Test
    void 좌표_없는_축제는_담지_않는다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        StandardFestival 좌표없음 = new StandardFestival(
                "좌표없는축제", null, "주소 " + region.getSigungu(), region.getSigungu(),
                null, null, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 4),
                null, null, null, null);
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(좌표없음), 1)
                : StandardFestivalResult.empty());

        int saved = refreshService.refresh(FIRST_RUN).saved();

        assertEquals(0, saved);
        assertTrue(festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).isEmpty());
    }

    /**
     * <b>이 테스트가 이 작업에서 가장 비싼 실수를 막는다.</b>
     *
     * <p>둘째 페이지가 깨진 회차는 "이번에 안 온 것 = 취소됨" 이 성립하지 않는다. 그때 정리하면 멀쩡한
     * 축제를 우리가 없애고, 되돌릴 방법이 없다.
     */
    @Test
    void 페이지가_깨진_회차는_기존_축제를_지우지_않는다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();

        // 1회차 — 온전히 받아 두 건을 심는다.
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(
                        List.of(축제(region.getSigungu(), "먼저있던축제"), 축제(region.getSigungu(), "또다른축제")), 2)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);
        assertEquals(2, festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).size());

        // 2회차 — 첫 페이지는 한 건만 주고 둘째 페이지가 깨진다. 전체가 더 있다고 말한다.
        stub.respond(page -> {
            if (page == 1) {
                return new StandardFestivalResult(List.of(축제(region.getSigungu(), "먼저있던축제")), 150);
            }
            throw new IllegalStateException("둘째 페이지가 깨졌다");
        });
        refreshService.refresh(SECOND_RUN);

        List<String> names = festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).stream()
                .map(FestivalPlace::getName)
                .toList();
        assertTrue(names.contains("또다른축제"),
                "온전하지 않은 회차가 멀쩡한 축제를 지웠다 — 되돌릴 수 없는 손실이다: " + names);
    }

    /** 온전히 받은 회차에서는 이번에 안 온 축제를 지운다 — 취소된 행이 영원히 남지 않게. */
    @Test
    void 온전한_회차는_이번에_안_온_축제를_지운다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();

        stub.respond(page -> page == 1
                ? new StandardFestivalResult(
                        List.of(축제(region.getSigungu(), "남을축제"), 축제(region.getSigungu(), "취소될축제")), 2)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);

        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "남을축제")), 1)
                : StandardFestivalResult.empty());
        refreshService.refresh(SECOND_RUN);

        List<String> names = festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).stream()
                .map(FestivalPlace::getName)
                .toList();
        assertEquals(List.of("남을축제"), names);
    }

    /** 같은 회차를 두 번 받아도 늘지 않는다 — 자연키가 막는다. */
    @Test
    void 같은_축제를_두_번_받아도_한_건이다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "같은축제")), 1)
                : StandardFestivalResult.empty());

        refreshService.refresh(FIRST_RUN);
        refreshService.refresh(SECOND_RUN);

        assertEquals(1, festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).size());
    }

    /**
     * <b>반쪽짜리 회차를 "다 됐다" 로 기록하지 않는다.</b>
     *
     * <p>둘째 페이지가 깨져도 첫 페이지 것은 저장되므로 <b>저장 건수가 양수</b>다. 그걸로 배치 마커를
     * 남기면 다음 갱신이 25일 막혀, 반쪽짜리 축제 목록을 그동안 그대로 쓰게 된다.
     */
    @Test
    void 페이지가_깨진_회차는_완료로_기록되지_않는다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> {
            if (page == 1) {
                return new StandardFestivalResult(List.of(축제(region.getSigungu(), "첫페이지축제")), 150);
            }
            throw new IllegalStateException("둘째 페이지가 깨졌다");
        });

        FestivalPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(FIRST_RUN);

        assertTrue(outcome.saved() > 0, "받은 것은 저장한다 — 전부 버리면 이번 달 내내 축제를 모른다");
        assertFalse(outcome.complete(), "온전하지 않은 회차를 완료로 기록하면 다음 갱신이 25일 막힌다");
    }

    /** 페이지 상한에 걸려 잘린 회차도 완료가 아니다 — 실패와 같은 이유다. */
    @Test
    void 상한에_걸려_잘린_회차도_완료가_아니다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        // 전체가 페이지 상한(20 × 100)을 훌쩍 넘는다고 말한다.
        stub.respond(page -> new StandardFestivalResult(
                List.of(축제(region.getSigungu(), "축제" + page)), 100_000));

        FestivalPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(FIRST_RUN);

        assertFalse(outcome.complete(), "못 받은 페이지가 있는데 완료로 치면 그만큼이 영영 안 채워진다");
    }

    /** 온전히 받은 회차는 완료다 — 한쪽만 보면 "항상 미완료" 가 초록이 된다. */
    @Test
    void 온전히_받은_회차는_완료다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "온전한축제")), 1)
                : StandardFestivalResult.empty());

        FestivalPlaceRefreshService.RefreshOutcome outcome = refreshService.refresh(FIRST_RUN);

        assertTrue(outcome.complete());
        assertTrue(outcome.saved() > 0);
    }

    /** 그날 안 여는 축제는 조회에 안 걸린다 — 코스에 올릴 수 없는 것이다. */
    @Test
    void 그날_안_여는_축제는_조회되지_않는다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "가을축제")), 1)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);

        assertFalse(festivalPlaceRepository.findOpenOn(region.getId(), DURING, 10).isEmpty());
        assertTrue(festivalPlaceRepository
                .findOpenOn(region.getId(), LocalDate.of(2026, 12, 25), 10).isEmpty());
    }

    /**
     * <b>축제가 국가유산 보충을 막지 않는다</b>(#433).
     *
     * <h2>왜 이걸 잠그나</h2>
     *
     * <p>축제를 볼거리에 <b>먼저</b> 붙이면 그 수가 {@code needsMoreSights()}(18개)에 섞여 "충분" 판정을
     * 받는다. 그러면 국가유산·인허가 보충이 통째로 안 돈다.
     *
     * <p>실측이 그 대가를 보여준다 — 우리 DB 볼거리가 지역당 <b>평균 82개</b>인데, TourAPI 15개 +
     * 축제 4건이 19개로 충분 판정을 받으면 그 82개를 아예 안 쓴다. <b>축제 몇 건을 얻고 후보 풀
     * 수십 개를 잃는</b> 셈이라 동선을 고를 여지가 그만큼 사라진다.
     *
     * <p>순서를 되돌려도 테스트가 초록이면 아무도 모른다 — 코스는 여전히 나오고 슬롯도 차기 때문이다.
     */
    @Test
    void 축제가_국가유산_보충을_막지_않는다() {
        Region region = 국가유산이_있는_지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(
                        축제(region.getSigungu(), "축제하나"), 축제(region.getSigungu(), "축제둘"),
                        축제(region.getSigungu(), "축제셋"), 축제(region.getSigungu(), "축제넷")), 4)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);

        // TourAPI 가 볼거리 15개만 준다 — 보충 문턱(18)에 못 미친다.
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지(15), 15));

        RegionPois pois = regionPoiService.collect(region.getId(), DURING);

        List<String> ids = pois.sights().stream().map(PoiCandidate::contentId).toList();
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("FST-")), "축제가 들어가야 한다: " + ids);
        assertTrue(ids.stream().anyMatch(id -> id.startsWith("HER-")),
                "국가유산 보충이 돌아야 한다 — 축제가 판정을 넘겨버리면 여기가 빈다: " + ids);
    }

    /** 축제가 <b>맨 앞</b>이다 — 첫 생성의 씨앗이 0이라 그 자리가 곧 군집의 시작점이 된다. */
    @Test
    void 축제가_볼거리_맨_앞에_온다() {
        Region region = 국가유산이_있는_지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "맨앞축제")), 1)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지(15), 15));

        RegionPois pois = regionPoiService.collect(region.getId(), DURING);

        assertTrue(pois.sights().get(0).contentId().startsWith("FST-"),
                "축제가 맨 앞이 아니면 첫 생성에서 씨앗이 되지 못한다");
    }

    /** 여행일을 모르면 축제를 안 넣는다 — 언제 여는지로 거를 수 없으면 끝난 축제를 올리게 된다. */
    @Test
    void 여행일을_모르면_축제를_넣지_않는다() {
        Region region = 국가유산이_있는_지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(List.of(축제(region.getSigungu(), "날짜없음축제")), 1)
                : StandardFestivalResult.empty());
        refreshService.refresh(FIRST_RUN);
        ((StubTourApiClient) tourApiClient).respond(() -> new TourPoiResult(관광지(15), 15));

        RegionPois pois = regionPoiService.collect(region.getId(), null);

        assertTrue(pois.sights().stream().noneMatch(c -> c.contentId().startsWith("FST-")));
    }

    /** TourAPI 볼거리 픽스처 — 좌표가 있어야 후보로 산다. */
    private static List<TourPoi> 관광지(int count) {
        List<TourPoi> pois = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pois.add(new TourPoi(
                    "C-" + i, 12, "VE", "관광지" + i, "주소",
                    36.5 + i * 0.001, 128.7 + i * 0.001, "http://img/" + i + ".jpg", null, null));
        }
        return pois;
    }

    /** 국가유산이 실제로 있는 지역이라야 "보충이 돌았나" 를 볼 수 있다. */
    private Region 국가유산이_있는_지역() {
        return regionRepository.findAll().stream()
                .filter(r -> !heritagePlaceRepository.findVisitableCandidates(r.getId(), 1).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("국가유산이 있는 지역이 없어 이 테스트가 성립하지 않는다"));
    }

    private StubFestivalStandardClient stub() {
        return (StubFestivalStandardClient) festivalStandardClient;
    }

    private static StandardFestival 축제(String sigungu, String name) {
        return new StandardFestival(
                name,
                "행사장 일원",
                "경상북도 " + sigungu + " 육사로 239",
                sigungu,
                36.5684,
                128.7294,
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 4),
                "축제 설명",
                "주관기관",
                "054-000-0000",
                "https://example.kr");
    }

    private Region 우리지역() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        FestivalStandardClient stubFestivalStandardClient() {
            return new StubFestivalStandardClient();
        }

        /**
         * 볼거리 수를 우리가 정하려면 TourAPI 도 잡아야 한다 — 축제가 <b>보충 판정을 넘겨버리는지</b>
         * 를 보는 것이 목적이라, "TourAPI 가 18개에 못 미치는 지역" 을 만들어야 한다.
         */
        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }
}
