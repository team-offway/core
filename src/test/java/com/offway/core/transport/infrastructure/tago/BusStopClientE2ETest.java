package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TAGO 버스정류소정보 실제 외부 호출 E2E — data.go.kr 을 stub 없이 직접 부른다. 네트워크 비결정성 탓에 CI 기본 실행에서
 * 제외한다({@code DATA_GO_KR_SERVICE_KEY} 있을 때만).
 *
 * <p>확인 대상은 "실제 외부와 우리 코드의 접점" 이다 — base·오퍼레이션 casing({@code ...InqireService} + 소문자),
 * 파라미터명({@code gpsLati}/{@code gpsLong}), 응답 필드명({@code nodeid}·{@code nodenm}·{@code citycode}·
 * {@code gpslati}/{@code gpslong}).
 *
 * <p><b>좌표는 TAGO 커버 지자체로 고른다.</b> TAGO 시내버스는 전국이 아니라 138개 지자체만 담고 서울조차 빠져 있다
 * (서울은 별도 TOPIS). 미커버 지역을 쓰면 접점이 멀쩡해도 결과가 비어 테스트가 거짓으로 실패한다.
 */
@EnabledIfEnvironmentVariable(named = "DATA_GO_KR_SERVICE_KEY", matches = ".+")
class BusStopClientE2ETest {

    /** 실호출 확인(2026-07-31) 138곳. 이보다 적어지면 페이지 크기·스키마가 바뀐 신호다. */
    private static final int EXPECTED_CITY_COUNT = 138;

    private static BusStopClient client() {
        ExternalApiProperties props = new ExternalApiProperties(
                new ExternalApiProperties.DataGoKr(System.getenv("DATA_GO_KR_SERVICE_KEY")), null);
        return new BusStopClientImpl(WebClient.builder().build(), props);
    }

    @Test
    void 커버_도시_목록을_실제로_받아온다() {
        // 커버 판별의 근거 데이터다. 목록이 짧아지면 멀쩡한 지역이 "데이터 없음"으로 안내된다.
        BusCoverage coverage = client().coveredCities().orElseThrow(() -> new AssertionError("도시목록 조회 실패"));

        assertTrue(
                coverage.cities().size() >= EXPECTED_CITY_COUNT,
                "커버 지자체가 " + EXPECTED_CITY_COUNT + "곳보다 적다 — 페이지 크기나 응답 스키마가 바뀐 것: "
                        + coverage.cities().size());
        assertTrue(coverage.covers("강원특별자치도", "태백시"), "태백은 커버 대상이다");
        assertFalse(coverage.covers("강원특별자치도", "정선군"), "정선은 TAGO 미커버 — 커버로 뒤집혔다면 목록이 바뀐 것");
        assertFalse(coverage.covers("서울특별시", "종로구"), "서울은 TAGO 대상이 아니다(TOPIS 별도)");
    }

    /** 태백(32050)·춘천(32010) — 실호출로 커버가 확인된 지자체. 한 곳만 쓰면 그 지자체 특수성에 기댈 수 있어 둘을 본다. */
    @ParameterizedTest
    @CsvSource({"37.1641, 128.9856, 32050", "37.8853, 127.7169, 32010"})
    void 커버되는_지자체는_실제_정류소를_돌려준다(double lat, double lng, int expectedCityCode) {
        BusStopAccess result = client().nearbyStops(lat, lng);

        if (!(result instanceof BusStopAccess.Available available)) {
            throw new AssertionError("커버 지자체인데 정류소가 없다 — 접점 또는 커버리지가 바뀐 것. 실제 결과: " + result);
        }
        BusStop nearest = available.nearest();
        assertNotNull(nearest.nodeId(), "정류소 코드가 있어야 한다 — 없으면 응답 필드명이 바뀐 것");
        assertFalse(nearest.name().isBlank(), "정류소명이 있어야 한다");
        assertTrue(nearest.cityCode() == expectedCityCode, "도시코드가 기대와 달라졌다: " + nearest.cityCode());
        assertTrue(nearest.lat() > 33 && nearest.lat() < 39, "위도가 한반도 범위여야 한다: " + nearest.lat());
        assertTrue(nearest.lng() > 124 && nearest.lng() < 132, "경도가 한반도 범위여야 한다: " + nearest.lng());
    }
}
