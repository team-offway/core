package com.offway.core.transport.infrastructure.tmap;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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

        Optional<TmapRoute> route = client(body).carRoute(SEOUL, BUSAN);

        assertTrue(route.isPresent());
        assertEquals(25, route.get().durationMinutes()); // 1500초 → 25분
        assertEquals(12.0, route.get().distanceKm(), 0.001); // 12000m → 12km
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TMAP 호출이 일어났다");
                })
                .build();

        assertTrue(new TmapClientImpl(neverCalled, NO_KEY, new NoOpCallRecorder()).carRoute(SEOUL, BUSAN).isEmpty());
    }

    @Test
    void 경로가_없으면_빈결과로_폴백을_유도한다() {
        assertTrue(client("{\"type\":\"FeatureCollection\",\"features\":[]}").carRoute(SEOUL, BUSAN).isEmpty());
    }

    @Test
    void 호출_실패는_예외가_아니라_빈결과다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
                .build();

        assertTrue(new TmapClientImpl(stubbing(error), WITH_KEY, new NoOpCallRecorder()).carRoute(SEOUL, BUSAN).isEmpty());
    }
}
