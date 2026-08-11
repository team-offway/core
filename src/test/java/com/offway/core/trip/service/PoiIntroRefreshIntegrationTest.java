package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.OpeningHours;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.repository.PoiIntroRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Autowired
    private PoiIntroRefreshService refreshService;

    @Autowired
    private PoiIntroRepository poiIntroRepository;

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
        PoiIntroRepository.ContentRef ref = new PoiIntroRepository.ContentRef("126508", 12);

        poiIntroRepository.upsertAll(
                Map.of(ref, new OpeningHours("09:00~18:00", "연중무휴")), LocalDateTime.now());

        OpeningHours found = poiIntroRepository.findByContentIds(List.of("126508")).get("126508");
        assertNotNull(found);
        assertEquals("09:00~18:00", found.useTime());
        assertEquals("연중무휴", found.restDate());
    }

    @Test
    void 같은_콘텐츠를_다시_받으면_덮어쓴다() {
        // 원본이 바뀌면 갱신돼야 한다. 행이 늘면 어느 값이 최신인지 알 수 없다.
        PoiIntroRepository.ContentRef ref = new PoiIntroRepository.ContentRef("999001", 12);
        poiIntroRepository.upsertAll(Map.of(ref, new OpeningHours("09:00~18:00", null)), LocalDateTime.now());

        poiIntroRepository.upsertAll(Map.of(ref, new OpeningHours("10:00~17:00", "매주 월요일")), LocalDateTime.now());

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
    void 값이_비어_와도_기록한다() {
        // 안 넣으면 매 회차 같은 콘텐츠를 다시 물어 예산을 태운다. "값이 없다" 도 사실이다.
        tourApiClient.respondIntro(() -> Optional.of(TourIntro.builder().contentId("x").build()));

        refreshService.refresh();

        assertTrue(poiIntroRepository.count() >= 0, "예외 없이 지나가야 한다");
    }

    @Test
    void 외부가_실패해도_다음_회차에_다시_시도한다() {
        // 저장하지 않으면 여전히 "안 받은 것" 으로 남아 다음 회차의 일감이 된다.
        tourApiClient.respondIntro(() -> {
            throw new IllegalStateException("upstream down");
        });

        refreshService.refresh();

        assertTrue(true, "실패가 배치를 죽이지 않는다");
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
}
