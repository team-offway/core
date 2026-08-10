package com.offway.core.weather.infrastructure.kma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.domain.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * 응답 하나가 이미 5일치를 담는데 Day 마다 다시 부르고 있었다 — 3일 코스 생성 4.81초 중 3.45초가
     * 이 중복이었다(#207). 격자·발표시각이 같으면 응답이 완전히 같으므로 한 번만 부르고 나눠 쓴다.
     */
    @Test
    void 같은_격자_같은_발표시각이면_한_번만_호출해_여러_날을_답한다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":[
                  {"category":"TMN","fcstDate":"20260501","fcstTime":"0600","fcstValue":"12.0"},
                  {"category":"TMX","fcstDate":"20260501","fcstTime":"1500","fcstValue":"23.0"},
                  {"category":"SKY","fcstDate":"20260501","fcstTime":"1200","fcstValue":"1"},
                  {"category":"TMN","fcstDate":"20260502","fcstTime":"0600","fcstValue":"14.0"},
                  {"category":"TMX","fcstDate":"20260502","fcstTime":"1500","fcstValue":"25.0"},
                  {"category":"SKY","fcstDate":"20260502","fcstTime":"1200","fcstValue":"4"}
                ]}}}}""";
        AtomicInteger calls = new AtomicInteger();
        // ClientResponse 는 한 번만 소비되므로 호출마다 새로 만든다.
        WebClient counting = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        KmaWeatherClient client = new KmaWeatherClientImpl(counting, WITH_KEY);

        DailyWeather first = client.dailyForecast(37.5665, 126.9780, DATE).orElseThrow();
        DailyWeather second = client.dailyForecast(37.5665, 126.9780, DATE.plusDays(1)).orElseThrow();

        assertEquals(1, calls.get(), "같은 격자·발표시각인데 두 번 불렀다");
        // 날짜별로 다른 값이 나와야 한다 — 한 번 부른 뒤 아무 날짜에나 같은 값을 주면 안 된다.
        assertEquals(12, first.minTemp());
        assertEquals(14, second.minTemp());
        assertEquals(SkyState.CLEAR, first.sky());
        assertEquals(SkyState.CLOUDY, second.sky());
    }

    @Test
    void 격자가_다르면_따로_부른다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":[
                  {"category":"TMN","fcstDate":"20260501","fcstTime":"0600","fcstValue":"12.0"}
                ]}}}}""";
        AtomicInteger calls = new AtomicInteger();
        WebClient counting = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .build();
        KmaWeatherClient client = new KmaWeatherClientImpl(counting, WITH_KEY);

        client.dailyForecast(37.5665, 126.9780, DATE); // 서울
        client.dailyForecast(35.1796, 129.0756, DATE); // 부산

        assertEquals(2, calls.get());
    }

    /** 응답 전체를 접게 되면서, 날짜 하나가 깨졌을 때 멀쩡한 날까지 버려지지 않는지 확인한다. */
    @Test
    void 예보일_형식이_깨진_항목이_있어도_나머지_날은_살린다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":[
                  {"category":"TMN","fcstDate":"깨진값","fcstTime":"0600","fcstValue":"9.0"},
                  {"category":"TMN","fcstDate":"20260501","fcstTime":"0600","fcstValue":"12.0"},
                  {"category":"TMX","fcstDate":"20260501","fcstTime":"1500","fcstValue":"23.0"}
                ]}}}}""";

        DailyWeather weather = client(body).dailyForecast(37.5665, 126.9780, DATE).orElseThrow();

        assertEquals(12, weather.minTemp());
        assertEquals(23, weather.maxTemp());
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
