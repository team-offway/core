package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCachePolicy;
import com.offway.core.transport.domain.Departure;
import com.offway.core.transport.domain.TransitMode;
import com.offway.core.transport.infrastructure.tago.StubTransitLegClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 캐시 스위치가 <b>수단마다 따로</b> 듣는지(#414 · #403).
 *
 * <p>스위치는 API 별인데 {@link com.offway.core.common.cache.ExternalDataCache} 는 스위치를 하나만 받는다.
 * 셋을 한 캐시에 담으면 하나를 끌 때 <b>나머지 둘까지 캐시를 잃어</b>, 켜 둔 수단이 열 때마다 실호출로
 * 나간다 — 이 기능이 줄이려는 것이 정확히 그 호출량이다.
 *
 * <p>결과값으로는 이 회귀를 못 잡는다(캐시가 있든 없든 같은 시간표가 나온다). 그래서 <b>호출 건수</b>를
 * 센다.
 */
class TransitDepartureCacheTest {

    private static final LocalDate 오늘 = LocalDate.of(2026, 9, 5);

    private static List<Departure> 한편() {
        LocalDateTime depart = LocalDateTime.of(2026, 9, 5, 7, 20);
        return List.of(new Departure("우등", depart, depart.plusMinutes(150)));
    }

    private static StubTransitLegClient 준비된_stub() {
        StubTransitLegClient stub = new StubTransitLegClient();
        stub.respondDepartures(TransitDepartureCacheTest::한편);
        return stub;
    }

    private void 두_번_조회(TransitDepartureService service, TransitMode mode) {
        service.departures(mode, "DEP", "ARR", 오늘, 오늘);
        service.departures(mode, "DEP", "ARR", 오늘, 오늘);
    }

    @Test
    void 캐시가_켜져_있으면_같은_구간을_한_번만_묻는다() {
        StubTransitLegClient stub = 준비된_stub();
        TransitDepartureService service = new TransitDepartureService(stub, ExternalApiCachePolicy.ALWAYS_CACHE);

        두_번_조회(service, TransitMode.INTERCITY_BUS);

        assertEquals(1, stub.departureAsks().size(), stub.departureAsks().toString());
    }

    /**
     * <b>여객선 스위치를 껐는데 시외버스가 캐시를 잃으면 안 된다.</b>
     *
     * <p>한 캐시에 셋을 담던 시절의 회귀다. 어드민이 항로 하나를 실시간으로 보려고 스위치를 내리면,
     * 아무 상관 없는 버스 시간표가 열릴 때마다 TAGO 로 나갔다.
     */
    @Test
    void 한_수단의_스위치를_꺼도_다른_수단은_캐시를_쓴다() {
        StubTransitLegClient stub = 준비된_stub();
        ExternalApiCachePolicy 여객선만_끔 = api -> api != ExternalApi.SHIP_INFO;
        TransitDepartureService service = new TransitDepartureService(stub, 여객선만_끔);

        두_번_조회(service, TransitMode.INTERCITY_BUS);

        assertEquals(1, stub.departureAsks().size(),
                "여객선 스위치를 껐는데 시외버스까지 캐시를 잃었다: " + stub.departureAsks());
    }

    /** 끈 수단은 실제로 매번 나간다 — 스위치가 듣는다는 반대편 확인이다. */
    @Test
    void 스위치를_끈_수단은_매번_실호출한다() {
        StubTransitLegClient stub = 준비된_stub();
        TransitDepartureService service = new TransitDepartureService(stub, api -> api != ExternalApi.SHIP_INFO);

        두_번_조회(service, TransitMode.FERRY);

        assertEquals(2, stub.departureAsks().size(), stub.departureAsks().toString());
    }
}
