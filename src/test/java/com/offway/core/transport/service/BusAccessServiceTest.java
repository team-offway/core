package com.offway.core.transport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
import com.offway.core.transport.infrastructure.tago.StubBusStopClient;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BusAccessService — 정류소 조회 결과 전달과 캐시 동작. 외부 경계는 stub 클라이언트로 격리.
 *
 * <p><b>의도적 미검증</b>: 조회 실패 시 stale 을 유지하는 분기는 TTL(24h) 만료가 필요해 여기서 확인하지 않는다. 캐시에
 * 시계를 주입하는 구조가 아니라, 억지로 검증하려면 프로덕션 코드를 테스트용으로 비트는 대가가 더 크다.
 */
class BusAccessServiceTest {

    private static final double LAT = 37.3878;
    private static final double LNG = 128.6716;
    private static final BusStop TERMINAL = new BusStop("GMB165", "정선터미널", 32020, 37.3801, 128.6604);

    @Test
    void 정류소가_있으면_그대로_돌려준다() {
        StubBusStopClient stub = new StubBusStopClient();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));

        BusStopAccess result = new BusAccessService(stub).nearbyStops(LAT, LNG);

        assertEquals("정선터미널", assertInstanceOf(BusStopAccess.Available.class, result)
                .nearest()
                .name());
    }

    @Test
    void 주변에_정류소가_없다는_결과도_그대로_전달한다() {
        // "없음"은 오류가 아니라 안내할 정상 결과다 — 조회 불가로 뭉뚱그리면 안내 문구가 사라진다.
        StubBusStopClient stub = new StubBusStopClient();
        stub.respond(BusStopAccess.NoStopNearby::new);

        assertInstanceOf(BusStopAccess.NoStopNearby.class, new BusAccessService(stub).nearbyStops(LAT, LNG));
    }

    @Test
    void 같은_좌표를_다시_물으면_외부를_다시_부르지_않는다() {
        StubBusStopClient stub = new StubBusStopClient();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub);

        service.nearbyStops(LAT, LNG);
        service.nearbyStops(LAT, LNG);

        assertEquals(1, stub.callCount());
    }

    @Test
    void 미세하게_다른_좌표는_같은_캐시_키로_묶인다() {
        // 좌표를 그대로 키로 쓰면 소수점 끝자리마다 키가 생겨 캐시가 무력화되고 맵이 무한히 커진다.
        StubBusStopClient stub = new StubBusStopClient();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub);

        service.nearbyStops(LAT, LNG);
        service.nearbyStops(LAT + 0.000001, LNG + 0.000001);

        assertEquals(1, stub.callCount());
    }

    @Test
    void 캐시를_비우면_다시_조회한다() {
        StubBusStopClient stub = new StubBusStopClient();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub);

        service.nearbyStops(LAT, LNG);
        service.evictCache();
        service.nearbyStops(LAT, LNG);

        assertEquals(2, stub.callCount());
    }
}
