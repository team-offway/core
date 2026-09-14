package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.CrowdChip;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.crowd.AttractionCrowdClient;
import com.offway.core.trip.infrastructure.crowd.StubAttractionCrowdClient;
import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import com.offway.core.trip.repository.AttractionCrowdForecastRepository;
import com.offway.core.trip.service.dto.CourseCrowd;
import java.time.LocalDate;
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
 * 집중률 예보가 <b>실제로 적재되고 칩으로 나오는가</b>(#565).
 *
 * <p>여기서 잠그는 것은 넷이다.
 *
 * <ol>
 *   <li>지역마다 갈아 끼우는가 — 받은 지역만 바뀌고 못 받은 지역은 옛 값을 유지하는가
 *   <li>호출이 실패한 지역의 예보를 <b>지우지 않는가</b>
 *   <li>응답이 빈 지역을 <b>비우지 않는가</b> — 못 받은 것과 사라진 것은 다르다
 *   <li>지나간 날짜가 쓸리는가 — 표가 날마다 커지지 않게
 * </ol>
 */
@SpringBootTest
@Transactional
class AttractionCrowdIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Autowired
    private AttractionCrowdRefreshService refreshService;

    @Autowired
    private AttractionCrowdForecastRepository forecastRepository;

    @Autowired
    private AttractionCrowdService crowdService;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private AttractionCrowdClient crowdClient;

    @Test
    void 받은_예보를_지역별로_적재한다() {
        Region region = 지역(0);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 45.17), 예보("가의도", TODAY.plusDays(2), 88.0))
                : List.of());

        AttractionCrowdRefreshService.RefreshOutcome outcome = refreshService.refresh(TODAY);

        assertEquals(1, outcome.regionsUpdated());
        assertEquals(2, outcome.rowsSaved());
        assertEquals(2, forecastRepository.findByRegionAndDates(
                region.getId(), List.of(TODAY, TODAY.plusDays(2))).size());
    }

    /**
     * <b>호출이 실패한 지역의 예보를 지우지 않는다.</b>
     *
     * <p>이 배치는 지역마다 호출이 갈린다. 전역으로 "이번에 안 온 것" 을 지우면 실패한 지역의 예보가
     * 함께 사라져, 못 받은 것과 사라진 것이 구분되지 않는다.
     */
    @Test
    void 호출이_실패한_지역은_옛_예보를_유지한다() {
        Region region = 지역(0);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 95.0))
                : List.of());
        refreshService.refresh(TODAY);
        assertEquals(1, forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY)).size());

        stub().respond(legalCode -> {
            throw TourApiException.crowdRateLookupFailed(new IllegalStateException("외부가 죽었다"));
        });
        AttractionCrowdRefreshService.RefreshOutcome outcome = refreshService.refresh(TODAY);

        assertTrue(outcome.regionsFailed() > 0);
        assertEquals(0, outcome.regionsUpdated());
        assertEquals(1, forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY)).size(),
                "실패한 회차가 멀쩡한 예보를 지우면 칩이 통째로 사라진다");
    }

    /**
     * <b>응답이 빈 지역을 비우지 않는다.</b>
     *
     * <p>예보가 없는 지역이 16곳이다. 빈 응답으로 갈아 끼우면 "외부가 잠깐 비어 왔다" 와 "그 지역에
     * 관광지가 없어졌다" 가 같아진다(성능 규약 "빈 응답을 성공으로 캐시하지 않는다").
     */
    @Test
    void 응답이_빈_지역은_옛_예보를_유지한다() {
        Region region = 지역(0);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 95.0))
                : List.of());
        refreshService.refresh(TODAY);

        stub().respond(legalCode -> List.of());
        AttractionCrowdRefreshService.RefreshOutcome outcome = refreshService.refresh(TODAY);

        assertEquals(0, outcome.regionsUpdated());
        assertTrue(outcome.regionsEmpty() > 0);
        assertEquals(1, forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY)).size());
    }

    /** 같은 지역을 다시 받으면 통째로 갈아 끼운다 — 예보는 날마다 다시 계산돼 온다. */
    @Test
    void 같은_지역을_다시_받으면_갈아_끼운다() {
        Region region = 지역(0);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 10.0), 예보("없어질곳", TODAY, 90.0))
                : List.of());
        refreshService.refresh(TODAY);
        assertEquals(2, forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY)).size());

        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 95.0))
                : List.of());
        refreshService.refresh(TODAY);

        var remaining = forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY));
        assertEquals(1, remaining.size(), "목록에서 빠진 관광지가 남아 있으면 옛 예보로 칩이 뜬다");
        assertEquals(95.0, remaining.getFirst().getRate());
    }

    /**
     * <b>지나간 날짜는 쓸린다.</b> 지역별 갈아 끼우기만으로는 호출이 계속 실패하는 지역의 옛 행이
     * 영영 남는다 — 표가 날마다 커진다.
     */
    @Test
    void 지나간_날짜의_예보를_쓸어_낸다() {
        Region region = 지역(0);
        LocalDate yesterday = TODAY.minusDays(1);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY, 95.0))
                : List.of());
        refreshService.refresh(TODAY);

        // 하루가 지났다 — 어제 것은 이제 지난 날짜다.
        AttractionCrowdRefreshService.RefreshOutcome outcome = refreshService.refresh(TODAY.plusDays(1));

        assertTrue(forecastRepository.findByRegionAndDates(region.getId(), List.of(TODAY)).isEmpty(),
                "지난 날짜가 남으면 표가 날마다 커진다");
        assertTrue(outcome.sweptPastRows() > 0);
        assertTrue(forecastRepository.findByRegionAndDates(region.getId(), List.of(yesterday)).isEmpty());
    }

    /** 예보가 지난 날짜로 섞여 와도 넣지 않는다 — 넣어 봐야 바로 다음 쓸기에 지워진다. */
    @Test
    void 지난_날짜의_예보는_받아도_넣지_않는다() {
        Region region = 지역(0);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("가의도", TODAY.minusDays(3), 95.0),
                        예보("가의도", TODAY.plusDays(1), 95.0))
                : List.of());

        AttractionCrowdRefreshService.RefreshOutcome outcome = refreshService.refresh(TODAY);

        assertEquals(1, outcome.rowsSaved());
    }

    /**
     * <b>적재한 예보가 칩으로 나오는가</b> — 이것이 사용자가 보는 결과다.
     *
     * <p>이름으로 맞춘다(집중률이 콘텐츠 ID 를 안 준다). 맞는 이름이 없으면 지역 폴백으로 내려간다.
     */
    @Test
    void 적재한_예보가_그_장소_그_날짜의_칩이_된다() {
        Region region = 지역(0);
        LocalDate travelDate = TODAY.plusDays(3);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("붐비는곳", travelDate, 95.0), 예보("한산한곳", travelDate, 5.0),
                        예보("보통인곳", travelDate, 50.0))
                : List.of());
        refreshService.refresh(TODAY);

        CourseCrowd crowd =
                crowdService.forCourse(region.getId(), region.getLegalCode(), List.of(travelDate));

        assertEquals(CrowdChip.Level.BUSY, crowd.of("붐비는곳", travelDate).orElseThrow().level());
        assertEquals(CrowdChip.Basis.ATTRACTION_FORECAST,
                crowd.of("붐비는곳", travelDate).orElseThrow().basis());
        assertEquals(CrowdChip.Level.QUIET, crowd.of("한산한곳", travelDate).orElseThrow().level());
        assertTrue(crowd.of("보통인곳", travelDate).isEmpty(), "문턱 사이는 칩이 없다");
    }

    /**
     * <b>재어 본 장소는 지역 폴백을 타지 않는다</b> — 적재부터 이어서 확인한다.
     *
     * <p>집중률 50 인 장소가 문턱 사이라 칩이 없는데, 그 날 지역 요일계수가 1.4 를 넘으면 "토요일엔
     * 붐비는 지역" 이 붙어 버린다. 우리가 재어 놓고도 더 거친 말로 덮는 셈이다.
     *
     * <p>지역 패턴은 적재된 방문자 데이터에 달려 있어 이 테스트에서 강제할 수 없다. 그래서 폴백이
     * 있든 없든 <b>성립하는 형태</b>로 단언한다 — 보통인 곳은 칩이 없거나, 있더라도 지역 근거가
     * 아니어야 한다. 폴백 유무를 직접 만드는 분기는 {@code CourseCrowdTest} 가 잠근다.
     */
    @Test
    void 재어_본_장소는_보통이어도_지역_폴백을_타지_않는다() {
        Region region = 지역(0);
        LocalDate travelDate = TODAY.plusDays(3);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("보통인곳", travelDate, 50.0))
                : List.of());
        refreshService.refresh(TODAY);

        CourseCrowd crowd =
                crowdService.forCourse(region.getId(), region.getLegalCode(), List.of(travelDate));

        assertTrue(crowd.of("보통인곳", travelDate).isEmpty(),
                "재어 보고 보통이었는데 지역 값으로 덮으면 그 장소에 대해 틀린 말을 한다");
    }

    /**
     * <b>다른 날짜의 예보를 끌어 쓰지 않는다.</b> 칩이 답하는 질문이 "내가 갈 그 날" 이라, 옆 날짜 값을
     * 쓰면 그 자리에서 조용히 틀린다.
     */
    @Test
    void 다른_날짜의_예보를_끌어_쓰지_않는다() {
        Region region = 지역(0);
        LocalDate travelDate = TODAY.plusDays(3);
        stub().respond(legalCode -> region.getLegalCode().equals(legalCode)
                ? List.of(예보("붐비는곳", travelDate, 95.0))
                : List.of());
        refreshService.refresh(TODAY);

        CourseCrowd crowd = crowdService.forCourse(
                region.getId(), region.getLegalCode(), List.of(travelDate.plusDays(1)));

        assertTrue(crowd.of("붐비는곳", travelDate.plusDays(1)).isEmpty());
    }

    /** 날짜를 모르는 코스(저장 시 안 넣음)는 칩이 없다 — 지어내지 않는다. */
    @Test
    void 날짜를_모르면_칩이_없다() {
        Region region = 지역(0);

        CourseCrowd crowd =
                crowdService.forCourse(region.getId(), region.getLegalCode(), List.of());

        assertTrue(crowd.of("아무곳", TODAY).isEmpty());
    }

    private StubAttractionCrowdClient stub() {
        return (StubAttractionCrowdClient) crowdClient;
    }

    private static AttractionCrowd 예보(String name, LocalDate date, double rate) {
        return new AttractionCrowd(name, date, rate);
    }

    private Region 지역(int index) {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.stream()
                .filter(region -> region.getLegalCode() != null)
                .skip(index)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("법정동 코드를 가진 지역이 없어 이 테스트가 성립하지 않는다"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        AttractionCrowdClient stubAttractionCrowdClient() {
            return new StubAttractionCrowdClient();
        }
    }
}
