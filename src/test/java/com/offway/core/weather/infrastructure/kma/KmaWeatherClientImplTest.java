package com.offway.core.weather.infrastructure.kma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** KmaWeatherClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. */
class KmaWeatherClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);
    private static final LocalDate DATE = LocalDate.of(2026, 5, 1);

    private static WebClient stubbing(ClientResponse response) {
        return WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
    }

    private static KmaWeatherClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        return new KmaWeatherClientImpl(stubbing(response), WITH_KEY);
    }

    @Test
    void 카테고리별_예보를_하루_요약으로_집계한다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                "body":{"items":{"item":[
                  {"category":"TMN","fcstDate":"20260501","fcstTime":"0600","fcstValue":"12.0"},
                  {"category":"TMX","fcstDate":"20260501","fcstTime":"1500","fcstValue":"23.0"},
                  {"category":"SKY","fcstDate":"20260501","fcstTime":"1200","fcstValue":"3"},
                  {"category":"POP","fcstDate":"20260501","fcstTime":"1200","fcstValue":"30"},
                  {"category":"POP","fcstDate":"20260501","fcstTime":"1500","fcstValue":"60"},
                  {"category":"TMP","fcstDate":"20260502","fcstTime":"1200","fcstValue":"20"}
                ]}}}}""";

        Optional<DailyWeather> weather = client(body).dailyForecast(37.5665, 126.9780, DATE);

        assertTrue(weather.isPresent());
        DailyWeather w = weather.get();
        assertEquals(12, w.minTemp());
        assertEquals(23, w.maxTemp());
        assertEquals(SkyState.PARTLY_CLOUDY, w.sky()); // SKY 3
        assertEquals(60, w.rainProbability()); // POP 최대(30·60)
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 기상청 호출이 일어났다");
                })
                .build();

        assertTrue(new KmaWeatherClientImpl(neverCalled, NO_KEY)
                .dailyForecast(37.5665, 126.9780, DATE).isEmpty());
    }

    @Test
    void 해당_날짜_예보가_없으면_빈결과다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":[
                  {"category":"TMP","fcstDate":"20260502","fcstTime":"1200","fcstValue":"20"}
                ]}}}}""";

        assertTrue(client(body).dailyForecast(37.5665, 126.9780, DATE).isEmpty());
    }
}
