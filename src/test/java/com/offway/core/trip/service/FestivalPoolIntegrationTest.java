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
import com.offway.core.trip.repository.FestivalPlaceRepository;
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

    @Test
    void 우리_지역_축제만_저장한다() {
        Region region = 우리지역();
        StubFestivalStandardClient stub = stub();
        stub.respond(page -> page == 1
                ? new StandardFestivalResult(
                        List.of(축제(region.getSigungu(), "우리축제"), 축제("서울특별시종로구", "남의축제")), 2)
                : StandardFestivalResult.empty());

        int saved = refreshService.refresh(FIRST_RUN);

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

        int saved = refreshService.refresh(FIRST_RUN);

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
    }
}
