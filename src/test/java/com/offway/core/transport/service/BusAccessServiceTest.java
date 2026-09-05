package com.offway.core.transport.service;

import com.offway.core.common.external.ExternalApiCachePolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.transport.domain.BusCity;
import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
import com.offway.core.transport.infrastructure.tago.StubBusStopClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * BusAccessService — 커버리지 판정, 정류소 조회 결과 전달, 캐시 동작. 외부 경계는 stub 클라이언트로 격리.
 *
 * <p><b>의도적 미검증</b>: 조회 실패 시 stale 을 유지하는 분기는 TTL(24h) 만료가 필요해 여기서 확인하지 않는다. 캐시에
 * 시계를 주입하는 구조가 아니라, 억지로 검증하려면 프로덕션 코드를 테스트용으로 비트는 대가가 더 크다.
 */
class BusAccessServiceTest {

    private static final double LAT = 37.3878;
    private static final double LNG = 128.6716;
    private static final BusStop TERMINAL = new BusStop("GMB165", "태백터미널", 32050, 37.3801, 128.6604);

    /** TAGO 가 담는 지역 — 태백은 목록에 있고 정선은 없다(getCtyCodeList 실응답 기준). */
    private static final String SIDO = "강원특별자치도";
    private static final String COVERED = "태백시";
    private static final String UNCOVERED = "정선군";
    private static final BusCoverage COVERAGE = new BusCoverage(List.of(new BusCity(32050, "태백시")));

    private static StubBusStopClient stubCovering() {
        StubBusStopClient stub = new StubBusStopClient();
        stub.respondCoverage(() -> Optional.of(COVERAGE));
        return stub;
    }

    @Test
    void 정류소가_있으면_그대로_돌려준다() {
        StubBusStopClient stub = stubCovering();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));

        BusStopAccess result = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE).nearbyStops(SIDO, COVERED, LAT, LNG);

        assertEquals("태백터미널", assertInstanceOf(BusStopAccess.Available.class, result)
                .nearest()
                .name());
    }

    @Test
    void 주변에_정류소가_없다는_결과도_그대로_전달한다() {
        // "없음"은 오류가 아니라 안내할 정상 결과다 — 조회 불가로 뭉뚱그리면 안내 문구가 사라진다.
        StubBusStopClient stub = stubCovering();
        stub.respond(BusStopAccess.NoStopNearby::new);

        assertInstanceOf(
                BusStopAccess.NoStopNearby.class, new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE).nearbyStops(SIDO, COVERED, LAT, LNG));
    }

    @Test
    void 미커버_지역은_정류소를_조회하지_않고_데이터없음으로_답한다() {
        // 정선은 버스가 없는 게 아니라 TAGO 에 데이터가 없다. 조회해봐야 빈 결과라 "정류소 없음"으로 오인된다.
        StubBusStopClient stub = stubCovering();

        BusStopAccess result = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE).nearbyStops(SIDO, UNCOVERED, LAT, LNG);

        assertInstanceOf(BusStopAccess.NotCovered.class, result);
        assertEquals(0, stub.callCount());
    }

    @Test
    void 커버_목록을_얻지_못하면_조회_불가로_답한다() {
        // 커버 여부를 모르는 채 빈 결과를 "정류소 없음"이라 안내하면 틀린 말이 될 수 있다. 조용히 폴백한다.
        StubBusStopClient stub = new StubBusStopClient();
        stub.respondCoverage(Optional::empty);

        BusStopAccess result = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE).nearbyStops(SIDO, COVERED, LAT, LNG);

        assertInstanceOf(BusStopAccess.Unavailable.class, result);
        assertEquals(0, stub.callCount());
    }

    @Test
    void 커버_목록은_한_번만_조회한다() {
        // 138곳 목록은 거의 변하지 않는다. 지역마다 다시 부르면 쿼터만 태운다.
        StubBusStopClient stub = stubCovering();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE);

        service.nearbyStops(SIDO, COVERED, LAT, LNG);
        service.nearbyStops(SIDO, UNCOVERED, LAT, LNG);

        assertEquals(1, stub.coverageCallCount());
    }

    @Test
    void 같은_좌표를_다시_물으면_외부를_다시_부르지_않는다() {
        StubBusStopClient stub = stubCovering();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE);

        service.nearbyStops(SIDO, COVERED, LAT, LNG);
        service.nearbyStops(SIDO, COVERED, LAT, LNG);

        assertEquals(1, stub.callCount());
    }

    @Test
    void 미세하게_다른_좌표는_같은_캐시_키로_묶인다() {
        // 좌표를 그대로 키로 쓰면 소수점 끝자리마다 키가 생겨 캐시가 무력화되고 맵이 무한히 커진다.
        StubBusStopClient stub = stubCovering();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE);

        service.nearbyStops(SIDO, COVERED, LAT, LNG);
        service.nearbyStops(SIDO, COVERED, LAT + 0.000001, LNG + 0.000001);

        assertEquals(1, stub.callCount());
    }

    @Test
    void 캐시를_비우면_다시_조회한다() {
        StubBusStopClient stub = stubCovering();
        stub.respond(() -> new BusStopAccess.Available(List.of(TERMINAL)));
        BusAccessService service = new BusAccessService(stub, ExternalApiCachePolicy.ALWAYS_CACHE);

        service.nearbyStops(SIDO, COVERED, LAT, LNG);
        service.evictCache();
        service.nearbyStops(SIDO, COVERED, LAT, LNG);

        assertEquals(2, stub.callCount());
    }
}
