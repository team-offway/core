package com.offway.core.transport.infrastructure.tmap;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** TmapClientImpl stub 통합 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리한다. */
class TmapClientImplTest {

    private static final ExternalApiProperties WITH_KEY = new ExternalApiProperties(
            new ExternalApiProperties.DataGoKr(null), new ExternalApiProperties.Tmap("test-key"));
    private static final ExternalApiProperties NO_KEY = new ExternalApiProperties(
            new ExternalApiProperties.DataGoKr(null), new ExternalApiProperties.Tmap(null));

    private static final Coordinate SEOUL = new Coordinate(37.5665, 126.9780);
    private static final Coordinate BUSAN = new Coordinate(35.1796, 129.0756);

    private static WebClient stubbing(ClientResponse response) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static TmapClient client(String body) {
        return new TmapClientImpl(stubbing(json(body)), WITH_KEY, new NoOpCallRecorder());
    }

    @Test
    void 자동차_경로의_소요시간과_거리를_파싱한다() {
        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"totalDistance":12000,"totalTime":1500,"totalFare":0}}
                ]}""";

        CarRouteResult result = client(body).carRoute(SEOUL, BUSAN);

        TmapRoute route = assertInstanceOf(CarRouteResult.Found.class, result).route();
        assertEquals(25, route.durationMinutes()); // 1500초 → 25분
        assertEquals(12.0, route.distanceKm(), 0.001); // 12000m → 12km
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TMAP 호출이 일어났다");
                })
                .build();

        assertInstanceOf(
                CarRouteResult.Unavailable.class,
                new TmapClientImpl(neverCalled, NO_KEY, new NoOpCallRecorder()).carRoute(SEOUL, BUSAN));
    }

    @Test
    void 경로가_없으면_빈결과로_폴백을_유도한다() {
        assertInstanceOf(
                CarRouteResult.Unavailable.class,
                client("{\"type\":\"FeatureCollection\",\"features\":[]}").carRoute(SEOUL, BUSAN));
    }

    @Test
    void 호출_실패는_예외가_아니라_빈결과다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
                .build();

        assertInstanceOf(
                CarRouteResult.Unavailable.class,
                new TmapClientImpl(stubbing(error), WITH_KEY, new NoOpCallRecorder()).carRoute(SEOUL, BUSAN));
    }

    // ── 좌표 탓인 거절을 가려낸다 (#335) ────────────────────────────────────

    private static CarRouteResult reject(String body) {
        ClientResponse error = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        return new TmapClientImpl(stubbing(error), WITH_KEY, new NoOpCallRecorder()).carRoute(SEOUL, BUSAN);
    }

    /**
     * 운영에서 귀목봉(해발 1,036m 산 정상)이 받은 응답이다. 예외 클래스명은 {@code BadRequest} 하나라
     * 본문의 code 를 봐야만 "좌표가 도로에 안 붙는다" 를 알 수 있다.
     */
    @Test
    void 도로_링크가_없다는_거절은_좌표_탓으로_가른다() {
        String body = "{\"error\":{\"id\":\"x\",\"code\":\"1100\","
                + "\"message\":\"요청 데이터 오류입니다.([022011] 출발지 조건에 맞는 링크가 존재하지 않습니다.)\"}}";

        CarRouteResult result = reject(body);

        assertEquals(
                UnroutableReason.NO_ROAD_LINK,
                assertInstanceOf(CarRouteResult.Rejected.class, result).reason());
    }

    @Test
    void 한반도_범위_초과도_좌표_탓으로_가른다() {
        String body = "{\"error\":{\"code\":\"1009\","
                + "\"message\":\"입력된 좌표가 규정된 범위(한반도)를 초과하였습니다.\"}}";

        CarRouteResult result = reject(body);

        assertEquals(
                UnroutableReason.OUT_OF_BOUNDS,
                assertInstanceOf(CarRouteResult.Rejected.class, result).reason());
    }

    /** 본문 모양이 바뀔 수 있어 최상위 {@code code} 도 본다. 숫자로 와도 읽힌다. */
    @Test
    void code_가_최상위에_숫자로_와도_읽는다() {
        assertInstanceOf(CarRouteResult.Rejected.class, reject("{\"code\":1100}"));
    }

    /**
     * <b>모르는 것을 좌표 탓으로 몰지 않는다.</b> 일시적 오류 한 번에 멀쩡한 장소가 영구히 코스에서
     * 사라지는 쪽이, 틀린 이동시간이 한 번 더 나가는 것보다 나쁘다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"error\":{\"code\":\"9999\",\"message\":\"알 수 없는 오류\"}}",
        "{\"error\":{\"message\":\"code 가 없다\"}}",
        "{}",
        "not json at all",
    })
    void 모르는_사유는_좌표_탓으로_보지_않는다(String body) {
        assertInstanceOf(CarRouteResult.Unavailable.class, reject(body));
    }
}
