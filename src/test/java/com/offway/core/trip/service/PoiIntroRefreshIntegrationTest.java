package com.offway.core.trip.service;

import com.offway.core.leave.domain.StartDayLeave;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.itinerary.domain.Course;
import com.offway.core.itinerary.domain.DaySchedule;
import com.offway.core.itinerary.domain.Density;
import com.offway.core.itinerary.domain.Slot;
import com.offway.core.itinerary.domain.SlotDisplay;
import com.offway.core.itinerary.domain.SlotKind;
import com.offway.core.itinerary.domain.TimeOfDay;
import com.offway.core.itinerary.repository.CourseRepository;
import com.offway.core.transport.domain.TransportMode;
import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.domain.PoiIntro;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 운영시간 적재(#157) — <b>한 번 받은 것을 다시 안 받는가</b>.
 *
 * <p>슬롯마다 요청 시점에 부르면 코스 하나에 20건이 넘어 관광정보 한도(1,000)가 코스 40개면 마른다.
 * 콘텐츠당 한 번만 받는 것이 이 기능의 전제다.
 */
@SpringBootTest
class PoiIntroRefreshIntegrationTest {

    /** 일감 목록을 자르지 않고 통째로 보기 위한 상한 — 통합 테스트 DB 의 슬롯 수를 넉넉히 넘는다. */
    private static final int WHOLE_WORK_LIST = 10_000;

    @Autowired
    private PoiIntroRefreshService refreshService;

    @Autowired
    private PoiIntroRepository poiIntroRepository;

    @Autowired
    private CourseRepository courseRepository;

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
    void 받은_운영시간을_콘텐츠_id_로_찾는다() {
        PoiIntroRepository.ContentRef ref = PoiIntroRepository.ContentRef.of("126508", 12);

        poiIntroRepository.upsertAll(
                Map.of(ref, PoiIntro.builder().useTime("09:00~18:00").restDate("연중무휴").build()), LocalDateTime.now());

        OpeningHours found = poiIntroRepository.findByContentIds(List.of("126508")).get("126508");
        assertNotNull(found);
        assertEquals("09:00~18:00", found.useTime());
        assertEquals("연중무휴", found.restDate());
    }

    @Test
    void 같은_콘텐츠를_다시_받으면_덮어쓴다() {
        // 원본이 바뀌면 갱신돼야 한다. 행이 늘면 어느 값이 최신인지 알 수 없다.
        PoiIntroRepository.ContentRef ref = PoiIntroRepository.ContentRef.of("999001", 12);
        poiIntroRepository.upsertAll(Map.of(ref, PoiIntro.builder().useTime("09:00~18:00").restDate(null).build()), LocalDateTime.now());

        poiIntroRepository.upsertAll(Map.of(ref, PoiIntro.builder().useTime("10:00~17:00").restDate("매주 월요일").build()), LocalDateTime.now());

        OpeningHours found = poiIntroRepository.findByContentIds(List.of("999001")).get("999001");
        assertEquals("10:00~17:00", found.useTime());
        assertEquals("매주 월요일", found.restDate());
    }

    @Test
    void 조회하지_않은_콘텐츠는_키가_없다() {
        // null 을 돌려주면 "값이 없다" 와 "아직 안 받았다" 가 구분되지 않는다.
        assertTrue(poiIntroRepository.findByContentIds(List.of("존재하지-않는-id")).isEmpty());
    }

    @Test
    void 값이_비어_와도_기록해_매_회차_다시_묻지_않는다() {
        // 안 넣으면 매 회차 같은 콘텐츠를 다시 물어 예산을 태운다. "값이 없다" 도 사실이다.
        String contentId = persistSlotNeedingHours(12);
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder().contentId(contentId).build()));

        refreshService.refresh();

        OpeningHours stored = poiIntroRepository.findByContentIds(List.of(contentId)).get(contentId);
        assertNotNull(stored, "빈 응답도 행으로 남아야 다음 회차가 같은 것을 다시 묻지 않는다");
        assertNull(stored.useTime());
        assertNull(stored.restDate());
        assertFalse(workListContentIds().contains(contentId), "방금 받은 빈 행은 곧바로 다시 일감이 되지 않는다");
    }

    @Test
    void 빈_행은_재시도_기간이_지나면_다시_일감이_된다() {
        // 빈 값을 영구 캐시로 굳히면 원본이 나중에 운영시간을 채워도 우리는 영영 모른다.
        String contentId = persistSlotNeedingHours(12);
        PoiIntroRepository.ContentRef ref = PoiIntroRepository.ContentRef.of(contentId, 12);
        poiIntroRepository.upsertAll(Map.of(ref, PoiIntro.builder().build()),
                LocalDateTime.now().minus(PoiIntroRefreshService.EMPTY_RETRY_INTERVAL).minusDays(1));

        assertTrue(workListContentIds().contains(contentId), "재시도 기간이 지난 빈 행은 다시 물어야 한다");

        // 값이 채워지면 그때부터는 다시 묻지 않는다 — 재시도 대상은 어디까지나 "빈 행" 이다.
        poiIntroRepository.upsertAll(Map.of(ref, PoiIntro.builder().useTime("09:00~18:00").restDate("연중무휴").build()),
                LocalDateTime.now().minusYears(1));

        assertFalse(workListContentIds().contains(contentId), "채워진 행은 오래돼도 다시 묻지 않는다");
    }

    @Test
    void 외부가_실패해도_다음_회차에_다시_시도한다() {
        // 저장하지 않으면 여전히 "안 받은 것" 으로 남아 다음 회차의 일감이 된다.
        String contentId = persistSlotNeedingHours(12);
        tourApiClient.respondIntro(() -> {
            throw new IllegalStateException("upstream down");
        });

        refreshService.refresh();

        assertTrue(poiIntroRepository.findByContentIds(List.of(contentId)).isEmpty(),
                "실패는 아무것도 남기지 않는다 — 남기면 다음 회차의 일감에서 빠진다");

        tourApiClient.respondIntro(() -> Optional.of(
                TourIntro.builder().contentId(contentId).useTime("10:00~17:00").build()));
        refreshService.refresh();

        OpeningHours retried = poiIntroRepository.findByContentIds(List.of(contentId)).get(contentId);
        assertNotNull(retried, "다음 회차가 같은 콘텐츠를 다시 집어야 한다");
        assertEquals("10:00~17:00", retried.useTime());
    }

    @Test
    void 오늘_이미_돌았으면_외부를_부르지_않는다() {
        // fixedDelay 는 프로세스가 사는 동안의 간격이라 재배포하면 주기가 처음부터 다시 센다.
        refreshService.refreshIfStale();

        tourApiClient.respondIntro(() -> {
            throw new AssertionError("오늘 이미 돌았는데 관광 API 를 불렀다");
        });
        refreshService.refreshIfStale();
    }

    /**
     * 운영시간이 아직 없는 슬롯 하나를 코스에 담아 남기고 그 콘텐츠 id 를 돌려준다.
     *
     * <p>배치의 일감 목록은 별도 큐가 아니라 {@code slot} 테이블이다. 슬롯 없이 {@code refresh()} 를 부르면
     * 빈 목록에서 곧바로 돌아와, 무엇을 단언하든 통과한다.
     *
     * <p>콘텐츠 id 는 매번 새로 만든다 — 통합 테스트가 DB 를 공유해 고정 id 를 쓰면 앞 테스트가 남긴 행이
     * 시나리오를 바꾼다.
     */
    private String persistSlotNeedingHours(int contentTypeId) {
        String contentId = "intro-test-" + UUID.randomUUID();
        Slot slot = Slot.of(1, TimeOfDay.MORNING, SlotKind.SIGHT, contentId, contentTypeId,
                "운영시간 적재 테스트 장소", 37.5, 127.0, 0, SlotDisplay.none());
        courseRepository.save(Course.of(1L, Density.RELAXED, TransportMode.CAR,
                List.of(DaySchedule.of(1, List.of(slot))), LocalDate.now(), 1, StartDayLeave.FULL_DAY));
        return contentId;
    }

    /**
     * 지금 배치가 물어볼 콘텐츠 전부.
     *
     * <p>하루 예산이 아니라 상한 없는 목록을 본다 — 예산으로 자르면 다른 테스트가 남긴 슬롯이 앞자리를
     * 차지했을 때 "일감에 없다" 가 참인지 잘려나간 것인지 구분되지 않는다.
     */
    private List<String> workListContentIds() {
        return poiIntroRepository
                .findMissing(WHOLE_WORK_LIST, LocalDateTime.now().minus(PoiIntroRefreshService.EMPTY_RETRY_INTERVAL))
                .stream()
                .map(PoiIntroRepository.ContentRef::contentId)
                .toList();
    }
}
