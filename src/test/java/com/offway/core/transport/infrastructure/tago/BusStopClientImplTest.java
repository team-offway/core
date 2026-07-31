package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStopAccess;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** BusStopClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. */
class BusStopClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);
    private static final double LAT = 37.3878;
    private static final double LNG = 128.6716;

    /** TAGO 시내버스가 담는 지자체 수(실호출 확인). 이보다 적게 요청하면 뒷부분이 미커버로 오판된다. */
    private static final int TAGO_CITY_COUNT = 138;

    private static BusStopClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
        return new BusStopClientImpl(webClient, WITH_KEY);
    }

    @Test
    void 근접_정류소를_응답_순서대로_돌려준다() {
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"nodeid":"GMB165","nodenm":"정선터미널","citycode":32020,"gpslati":37.3801,"gpslong":128.6604},
                  {"nodeid":"GMB166","nodenm":"정선읍사무소","citycode":32020,"gpslati":37.3812,"gpslong":128.6631}
                ]}}}}""";

        BusStopAccess result = client(body).nearbyStops(LAT, LNG);

        BusStopAccess.Available available = assertInstanceOf(BusStopAccess.Available.class, result);
        assertEquals(2, available.stops().size());
        assertEquals("정선터미널", available.nearest().name()); // 응답이 근접순이라 첫 항목
        assertEquals(32020, available.nearest().cityCode());
    }

    @Test
    void 정류소가_하나면_item이_객체로_와도_처리한다() {
        // data.go.kr 은 결과가 하나면 item 을 배열이 아니라 객체 하나로 내려준다.
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
                  {"nodeid":"GMB165","nodenm":"정선터미널","citycode":32020,"gpslati":37.3801,"gpslong":128.6604}
                }}}}""";

        BusStopAccess result = client(body).nearbyStops(LAT, LNG);

        BusStopAccess.Available available = assertInstanceOf(BusStopAccess.Available.class, result);
        assertEquals("GMB165", available.nearest().nodeId());
    }

    @Test
    void 주변에_정류소가_없으면_NoStopNearby다() {
        // 결과가 없으면 items 가 빈 문자열로 온다. 인구감소지역엔 실제로 흔한 정상 결과다.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":""}}}""";

        assertInstanceOf(BusStopAccess.NoStopNearby.class, client(body).nearbyStops(LAT, LNG));
    }

    @Test
    void 항목은_있는데_전부_파싱실패면_Unavailable이다() {
        // 좌표 결측은 스키마 변경 신호다. 잘못된 "주변에 버스 없음" 안내를 막는다.
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"nodeid":"GMB165","nodenm":"정선터미널","citycode":32020}
                ]}}}}""";

        assertInstanceOf(BusStopAccess.Unavailable.class, client(body).nearbyStops(LAT, LNG));
    }

    @Test
    void 비정상_resultCode는_Unavailable이다() {
        String body = """
                {"response":{"header":{"resultCode":"99"},"body":{}}}""";

        assertInstanceOf(BusStopAccess.Unavailable.class, client(body).nearbyStops(LAT, LNG));
    }

    @Test
    void 커버_도시_목록을_파싱한다() {
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"citycode":32050,"cityname":"태백시"},
                  {"citycode":32020,"cityname":"원주시/횡성군"}
                ]}}}}""";

        BusCoverage coverage = client(body).coveredCities().orElseThrow();

        assertEquals(2, coverage.cities().size());
        assertTrue(coverage.covers("강원특별자치도", "태백시"));
        assertTrue(coverage.covers("강원특별자치도", "횡성군"));
    }

    @Test
    void 커버_목록이_비어_오면_실패로_본다() {
        // 빈 목록을 그대로 믿으면 전국이 미커버가 된다 — 89곳 전부 "데이터 없음"으로 안내하게 되는 사고다.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":""}}}""";

        assertTrue(client(body).coveredCities().isEmpty());
    }

    @Test
    void 커버_목록_조회가_실패하면_빈_Optional이다() {
        String body = """
                {"response":{"header":{"resultCode":"99"},"body":{}}}""";

        assertTrue(client(body).coveredCities().isEmpty());
    }

    @Test
    void 커버_목록은_전량을_한_번에_요청한다() {
        // TAGO 는 138곳을 준다. 기본 페이지 크기(10)로 요청하면 나머지가 통째로 미커버로 오판된다.
        AtomicReference<String> requestedUri = new AtomicReference<>();
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("""
                        {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
                          {"citycode":32050,"cityname":"태백시"}
                        }}}}""")
                .build();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestedUri.set(request.url().toString());
                    return Mono.just(response);
                })
                .build();

        new BusStopClientImpl(webClient, WITH_KEY).coveredCities();

        Matcher numOfRows = Pattern.compile("numOfRows=(\\d+)").matcher(requestedUri.get());
        assertTrue(numOfRows.find(), "numOfRows 파라미터가 없다: " + requestedUri.get());
        assertTrue(
                Integer.parseInt(numOfRows.group(1)) >= TAGO_CITY_COUNT,
                "전국 지자체 수(" + TAGO_CITY_COUNT + ")보다 작게 요청하면 뒷부분이 미커버로 오판된다: " + numOfRows.group(1));
    }

    @Test
    void 커버_목록도_키가_없으면_호출하지_않는다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TAGO 도시목록 호출이 일어났다");
                })
                .build();

        assertTrue(new BusStopClientImpl(neverCalled, NO_KEY).coveredCities().isEmpty());
    }

    @Test
    void 키가_없으면_호출하지_않고_Unavailable을_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TAGO 정류소 호출이 일어났다");
                })
                .build();

        assertInstanceOf(
                BusStopAccess.Unavailable.class, new BusStopClientImpl(neverCalled, NO_KEY).nearbyStops(LAT, LNG));
    }
}
