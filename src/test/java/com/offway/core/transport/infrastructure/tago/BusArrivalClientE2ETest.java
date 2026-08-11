package com.offway.core.transport.infrastructure.tago;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusArrival;
import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TAGO 버스도착정보 실제 외부 호출 E2E — data.go.kr 을 stub 없이 직접 부른다. CI 기본 실행에서 제외한다
 * ({@code DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p>정류소를 하드코딩하지 않고 근접조회로 먼저 얻어 그 정류소에 도착정보를 묻는다. 실제 사용 흐름과 같고, 무엇보다
 * <b>정류소 응답이 주는 도시코드가 도착정보 조회에 그대로 통하는지</b>(두 API 사이의 연결)를 확인할 수 있다. 하드코딩한
 * nodeId 로는 그 연결이 검증되지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class BusArrivalClientE2ETest {

    /** 춘천역(32010) — 실호출로 커버가 확인된 지자체이고 노선이 많아 도착 정보가 잡힐 확률이 높다. */
    private static final double CHUNCHEON_STATION_LAT = 37.8853;
    private static final double CHUNCHEON_STATION_LNG = 127.7169;

    private static ExternalApiProperties props() {
        return new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
    }

    @Test
    void 근접조회로_얻은_정류소에_도착정보를_실제로_조회한다() {
        WebClient webClient = WebClient.builder().build();
        BusStopAccess stops = new BusStopClientImpl(webClient, props(), new NoOpCallRecorder())
                .nearbyStops(CHUNCHEON_STATION_LAT, CHUNCHEON_STATION_LNG);
        if (!(stops instanceof BusStopAccess.Available available)) {
            throw new AssertionError("정류소 조회가 선행돼야 한다. 실제 결과: " + stops);
        }
        BusStop stop = available.nearest();

        BusArrivalStatus result = new BusArrivalClientImpl(webClient, props(), new NoOpCallRecorder()).arrivalsAt(stop);

        // 심야·비운행 시간대엔 오는 버스가 없을 수 있다(NoBusSoon, 정상). Unavailable 만이 접점 실패다.
        assertFalse(
                result instanceof BusArrivalStatus.Unavailable,
                "실호출 접점(경로·casing·도시코드 연결)이 정상이어야 한다 — 정류소=" + stop.nodeId() + " 도시코드=" + stop.cityCode());
        if (result instanceof BusArrivalStatus.Arriving arriving) {
            BusArrival soonest = arriving.soonest();
            assertFalse(soonest.routeNo().isBlank(), "노선번호가 있어야 한다 — 없으면 응답 필드명이 바뀐 것");
            assertTrue(soonest.arrivalSeconds() >= 0, "도착까지 남은 시간은 0 이상이어야 한다");
        }
    }
}
