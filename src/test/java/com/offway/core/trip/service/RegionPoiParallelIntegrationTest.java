package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import com.offway.core.trip.service.dto.RegionPois;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 코스 후보 조회가 <b>동시에</b> 나가는지(#434).
 *
 * <p><b>왜 재는가.</b> 볼거리·맛집·숙박을 각각 타입 스코프로 부르는데 순차로 돌고 있었다. TourAPI 가 느려지면
 * 호출 하나의 상한(6초)이 셋으로 곱해져 <b>18초가 그대로 사용자 대기 시간</b>이 된다 — 운영 로그에서
 * {@code tour 18002ms×3} 인 요청을 봤고, 거기에 날씨·열차가 얹혀 30초가 나왔다.
 *
 * <p><b>시간으로 단언한다.</b> "세 번 부른다" 는 순차일 때도 참이라 이 회귀를 못 잡는다. 각 호출을 일부러
 * 느리게 만들어 <b>합보다 짧게 끝나는지</b>를 봐야 병렬이 증명된다.
 */
@SpringBootTest
class RegionPoiParallelIntegrationTest {

    /** 축제 기간 필터가 결과를 흔들지 않게 고정한다 — 여기서 보는 것은 시간이다. */
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 9, 1);

    /** 호출 하나에 심는 지연. 실제 timeout(6초)보다 훨씬 짧게 둬 테스트가 느려지지 않게 한다. */
    private static final long CALL_DELAY_MILLIS = 400;

    /**
     * 셋을 순차로 돌면 최소 이만큼 걸린다. 병렬이면 하나치(400㎳)에 가깝다.
     *
     * <p>경계를 정확히 두 배(800㎳)로 잡았다 — 순차(1,200㎳)와 병렬(400㎳) 사이가 넓어, CI 가 흔들려도
     * 오탐이 나지 않으면서 회귀는 확실히 걸린다.
     */
    private static final long SEQUENTIAL_FLOOR_MILLIS = CALL_DELAY_MILLIS * 2;

    @Autowired
    private RegionPoiService regionPoiService;

    @Autowired
    private StubTourApiClient tourApiClient;

    @Autowired
    private RegionRepository regionRepository;

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }

    @Test
    void 세_스코프를_동시에_조회한다() {
        AtomicInteger calls = new AtomicInteger();
        tourApiClient.respond(() -> {
            calls.incrementAndGet();
            sleep(CALL_DELAY_MILLIS);
            return new TourPoiResult(List.of(poi()), 1);
        });
        Region region = regionRepository.findAll().getFirst();

        long startedAt = System.nanoTime();
        RegionPois pois = regionPoiService.collect(region.getId(), TRAVEL_DATE);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertEquals(3, calls.get(), "세 스코프를 각각 부른다 — 이건 순차일 때도 참이다");
        assertTrue(elapsedMillis < SEQUENTIAL_FLOOR_MILLIS,
                "세 조회가 순차로 돌았습니다 — %d㎳ 걸렸고 병렬이면 %d㎳ 안쪽이어야 합니다"
                        .formatted(elapsedMillis, SEQUENTIAL_FLOOR_MILLIS));
        assertTrue(pois.sights().size() + pois.foods().size() + pois.stays().size() > 0,
                "병렬로 돌려도 결과는 그대로 담겨야 한다");
    }

    /** 좌표·필수값이 있는 최소 후보 — 이 테스트가 보는 것은 시간이라 내용은 한 건이면 된다. */
    private static TourPoi poi() {
        return new TourPoi("126508", 12, "NA", "가사동백숲해변", "전남 완도군", 34.36, 126.92, "http://img/1.jpg", null, null);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
