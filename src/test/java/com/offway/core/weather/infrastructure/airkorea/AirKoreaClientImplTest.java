package com.offway.core.weather.infrastructure.airkorea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.infrastructure.airkorea.dto.AirQuality;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** AirKoreaClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. */
class AirKoreaClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

    private static WebClient stubbing(ClientResponse response) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
    }

    private static AirKoreaClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        return new AirKoreaClientImpl(stubbing(response), WITH_KEY);
    }

    @Test
    void 측정소들을_평균내고_통합등급은_최악을_취한다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":[
                  {"stationName":"A","pm10Value":"29","pm25Value":"15","khaiGrade":"1"},
                  {"stationName":"B","pm10Value":"21","pm25Value":"-","khaiGrade":"3"}
                ]}}}""";

        Optional<AirQuality> air = client(body).realtimeBySido("강원");

        assertTrue(air.isPresent());
        assertEquals(25, air.get().pm10()); // (29+21)/2
        assertEquals(15, air.get().pm25()); // "-" 결측치 제외 → 15만
        assertEquals(AirGrade.BAD, air.get().grade()); // 최악(khai 3)
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 에어코리아 호출이 일어났다");
                })
                .build();

        assertTrue(new AirKoreaClientImpl(neverCalled, NO_KEY).realtimeBySido("강원").isEmpty());
    }
}
