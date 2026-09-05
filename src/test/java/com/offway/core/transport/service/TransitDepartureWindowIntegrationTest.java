package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.infrastructure.tago.StubTransitLegClient;
import com.offway.core.transport.infrastructure.tago.TransitLegClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 시간표를 <b>언제 묻고 언제 안 묻는가</b>(#414).
 *
 * <p>여기서 잠그는 것은 결과값이 아니라 <b>호출 건수</b>다. 고속·시외버스는 오늘~+2일, 여객선은 오늘~+7일만
 * 배차를 답한다(실측 2026-08-31). 그 밖의 날짜를 물으면 <b>빈 결과가 오고 한도만 깎인다</b> — 그런데
 * 연차 기준으로 다음 달 코스를 짜는 서비스라 대부분의 코스가 창 밖이다.
 *
 * <p>결과만 보면 회귀를 못 잡는다. 창 밖을 물어도 빈 목록이 나와 "안 물었다" 와 구분되지 않기 때문이다.
 * 그래서 stub 이 물어본 날짜를 기억하고, 여기서는 <b>그 목록이 비었는지</b>를 본다.
 */
@SpringBootTest
class TransitDepartureWindowIntegrationTest {

    @TestConfiguration
    static class StubConfiguration {

        @Bean
        @Primary
        StubTransitLegClient stubTransitLegClient() {
            return new StubTransitLegClient();
        }
    }

    @Autowired
    private TransitDepartureService transitDepartureService;

    @Autowired
    private TransitLegClient transitLegClient;

    private StubTransitLegClient stub() {
        return (StubTransitLegClient) transitLegClient;
    }

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 5);

    private static List<Departure> 한편() {
        LocalDateTime depart = LocalDateTime.of(2026, 9, 5, 7, 20);
        return List.of(new Departure("우등", depart, depart.plusMinutes(150)));
    }

    @Test
    void 조회창_안이면_묻는다() {
        transitDepartureService.evictCache();
        stub().respondDepartures(TransitDepartureWindowIntegrationTest::한편);

        List<Departure> 결과 = transitDepartureService.departures(
                TransitMode.INTERCITY_BUS, "DEP-1", "ARR-1", 오늘, 오늘);

        assertEquals(1, 결과.size());
        assertEquals(List.of(오늘), stub().departureAsks());
    }

    /**
     * <b>창 밖이면 외부를 아예 안 친다.</b>
     *
     * <p>다음 달 코스를 여는 것이 이 서비스의 평상시 모습이다. 여기서 호출이 나가면 그 화면이 열릴 때마다
     * 한도가 깎이는데, 돌아오는 것은 빈 목록이라 사용자에게 아무 값도 안 준다.
     */
    @Test
    void 조회창_밖이면_묻지_않는다() {
        transitDepartureService.evictCache();
        stub().respondDepartures(TransitDepartureWindowIntegrationTest::한편);

        List<Departure> 결과 = transitDepartureService.departures(
                TransitMode.INTERCITY_BUS, "DEP-2", "ARR-2", 오늘.plusMonths(1), 오늘);

        assertTrue(결과.isEmpty());
        assertTrue(stub().departureAsks().isEmpty(), "창 밖 날짜를 물었다: " + stub().departureAsks());
    }

    /** 지난 날짜도 묻지 않는다 — 저장된 옛 코스를 여는 경로가 실제로 있다. */
    @Test
    void 지난_날짜는_묻지_않는다() {
        transitDepartureService.evictCache();
        stub().respondDepartures(TransitDepartureWindowIntegrationTest::한편);

        assertTrue(transitDepartureService
                .departures(TransitMode.INTERCITY_BUS, "DEP-3", "ARR-3", 오늘.minusDays(1), 오늘)
                .isEmpty());
        assertTrue(stub().departureAsks().isEmpty());
    }

    /**
     * <b>수단마다 창이 다르다.</b> 버스로는 못 묻는 +5일을 여객선으로는 묻는다.
     *
     * <p>하나로 뭉치면 주 몇 편만 뜨는 항로가 시간표를 영영 못 얻는다 — 그 항로는 울릉군처럼 배 말고 닿는
     * 수단이 없는 곳의 유일한 길이다.
     */
    @Test
    void 여객선은_버스보다_먼_날짜까지_묻는다() {
        transitDepartureService.evictCache();
        stub().respondDepartures(TransitDepartureWindowIntegrationTest::한편);
        LocalDate 닷새뒤 = 오늘.plusDays(5);

        transitDepartureService.departures(TransitMode.INTERCITY_BUS, "DEP-4", "ARR-4", 닷새뒤, 오늘);
        assertTrue(stub().departureAsks().isEmpty(), "버스가 조회창 밖을 물었다");

        transitDepartureService.departures(TransitMode.FERRY, "DEP-5", "ARR-5", 닷새뒤, 오늘);
        assertEquals(List.of(닷새뒤), stub().departureAsks());
    }

    /**
     * 같은 구간을 다시 열면 <b>외부를 다시 안 친다</b>.
     *
     * <p>여행 직전 코스는 자주 열리는 화면이라, 이 캐시가 없으면 열 때마다 한도를 깎는다.
     */
    @Test
    void 같은_구간을_다시_물으면_캐시가_답한다() {
        transitDepartureService.evictCache();
        stub().respondDepartures(TransitDepartureWindowIntegrationTest::한편);

        transitDepartureService.departures(TransitMode.INTERCITY_BUS, "DEP-6", "ARR-6", 오늘, 오늘);
        transitDepartureService.departures(TransitMode.INTERCITY_BUS, "DEP-6", "ARR-6", 오늘, 오늘);

        assertEquals(1, stub().departureAsks().size(), "같은 구간을 두 번 물었다: " + stub().departureAsks());
    }
}
