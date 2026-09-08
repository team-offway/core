package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.infrastructure.datalab.StubTourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.TourDataLabClient;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import com.offway.core.trip.infrastructure.tour.TourApiClient;
import com.offway.core.trip.infrastructure.tour.StubTourApiClient;
import com.offway.core.trip.repository.RegionVisitorDailyRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 방문자 적재가 <b>반쪽짜리 달을 굳히지 않는가</b>(#394).
 *
 * <h2>왜 이것을 잠그나</h2>
 *
 * <p>이 적재의 가드는 마커가 아니라 <b>데이터 자체</b>다 — 그 달의 행이 하나라도 있으면 다시 받지
 * 않는다. 그래서 <b>부분 수집을 저장하는 순간 그 달은 영영 안 채워진다.</b> 빠진 날만큼 요일 평균이
 * 틀어진 채로 굳고, 예외도 안 나고 화면도 멀쩡해 보인다.
 *
 * <p>가장 위험한 경우를 만든다 — 원본이 "아직 더 있다"({@code totalCount}) 고 해 놓고 <b>빈 페이지</b>를
 * 주는 상황이다. 이때 앞 페이지에서 모은 것을 저장하면 그대로 굳는다.
 */
@SpringBootTest
@Transactional
class RegionVisitorDailyBackfillIntegrationTest {

    /** 원본이 "아직 한참 남았다" 고 말하게 하는 값 — 페이지가 조기에 끝났음을 분명히 한다. */
    private static final int PLENTY_MORE = 999_999;

    @Autowired
    private RegionVisitorDailyRefreshService refreshService;

    @Autowired
    private RegionVisitorDailyRepository dailyRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private TourDataLabClient tourDataLabClient;

    /**
     * <b>더 있다는데 빈 페이지가 오면 그 달을 통째로 버린다.</b>
     *
     * <p>버그가 있으면 첫 페이지분만 저장되고, 그 뒤로 그 달은 "이미 받은 달" 로 판정돼 다시는 채워지지
     * 않는다. 저장이 0이어야 다음 회차에 온전히 다시 받는다.
     */
    @Test
    void 더_있다는데_빈_페이지가_오면_그_달을_저장하지_않는다() {
        StubTourDataLabClient stub = (StubTourDataLabClient) tourDataLabClient;
        RegionVisitor visitor = 우리지역_방문자();
        AtomicInteger calls = new AtomicInteger();
        // 홀수 호출(각 달의 첫 페이지)은 값이 있고, 짝수 호출(둘째 페이지)은 비어 있다.
        // 원본은 내내 "아직 더 있다" 고 말한다.
        stub.respond(() -> calls.incrementAndGet() % 2 == 1
                ? new TourVisitorResult(List.of(visitor), PLENTY_MORE)
                : new TourVisitorResult(List.of(), PLENTY_MORE));

        int inserted = refreshService.backfill(YearMonth.of(2099, 1));

        assertEquals(0, inserted, "부분 수집을 저장하면 그 달이 영영 안 채워진다");
        assertTrue(dailyRepository.latestDate().isEmpty(), "한 행이라도 남으면 그 달을 다시 안 받는다");
    }

    /**
     * 원본이 다 줬다고 하면(더 없음) 정상 저장된다 — 위 가드가 정상 경로까지 막지 않는지 본다.
     *
     * <p>달마다 <b>다른 날짜</b>를 준다. 같은 날짜를 15번 넣으면 UNIQUE 제약에 걸리는데, 운영에서는
     * 적재가 달마다 자기 트랜잭션을 열어 한 번 걸려도 다음이 멀쩡한 반면 이 테스트는 한 트랜잭션이라
     * 세션이 통째로 오염된다. 재현하려는 것은 그 상황이 아니다.
     */
    @Test
    void 원본이_다_줬다고_하면_저장한다() {
        StubTourDataLabClient stub = (StubTourDataLabClient) tourDataLabClient;
        Region region = 우리지역();
        AtomicInteger calls = new AtomicInteger();
        // totalCount 가 실제 건수와 같다 = 한 페이지로 끝났다.
        stub.respond(() -> new TourVisitorResult(
                List.of(방문자(region, LocalDate.of(2090, 1, 1).plusDays(calls.getAndIncrement()))), 1));

        int inserted = refreshService.backfill(YearMonth.of(2099, 1));

        assertFalse(dailyRepository.latestDate().isEmpty(), "정상 응답은 저장돼야 한다");
        assertTrue(inserted > 0, "저장된 행이 있어야 한다");
    }

    /** 미발행(빈 응답 + totalCount 0)은 저장하지 않는다 — 다음 회차에 다시 묻는다. */
    @Test
    void 미발행이면_저장하지_않는다() {
        StubTourDataLabClient stub = (StubTourDataLabClient) tourDataLabClient;
        stub.respond(TourVisitorResult::empty);

        int inserted = refreshService.backfill(YearMonth.of(2099, 1));

        assertEquals(0, inserted);
        assertTrue(dailyRepository.latestDate().isEmpty());
    }

    /** 우리 89곳 중 하나 — 다른 지역 코드를 주면 받아도 전부 걸러진다. */
    private Region 우리지역() {
        List<Region> regions = new ArrayList<>(regionRepository.findAll());
        assertFalse(regions.isEmpty(), "지역 마스터가 비어 있어 이 테스트가 성립하지 않는다");
        return regions.get(0);
    }

    private RegionVisitor 우리지역_방문자() {
        return 방문자(우리지역(), LocalDate.of(2098, 12, 1));
    }

    private static RegionVisitor 방문자(Region region, LocalDate date) {
        return new RegionVisitor(
                region.getLegalCode(), region.getSigungu(), date, VisitorType.DOMESTIC, 1000.0);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        TourDataLabClient stubTourDataLabClient() {
            return new StubTourDataLabClient();
        }

        @Bean
        @Primary
        TourApiClient stubTourApiClient() {
            return new StubTourApiClient();
        }
    }
}
