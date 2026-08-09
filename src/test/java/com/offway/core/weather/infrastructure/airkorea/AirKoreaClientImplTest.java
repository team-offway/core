package com.offway.core.weather.infrastructure.airkorea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
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

    /** 첫 호출은 504, 그 다음은 정상 — 간헐 장애를 재현한다. */
    private static AirKoreaClient flakyClient(String okBody, java.util.concurrent.atomic.AtomicInteger calls) {
        return new AirKoreaClientImpl(
                WebClient.builder()
                        .exchangeFunction(request -> {
                            if (calls.incrementAndGet() == 1) {
                                return Mono.error(new RuntimeException("SERVICETIMEOUT_ERROR"));
                            }
                            return Mono.just(ClientResponse.create(HttpStatus.OK)
                                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .body(okBody)
                                    .build());
                        })
                        .build(),
                WITH_KEY);
    }

    @Test
    void 간헐_실패는_한_번_다시_걸어_회복한다() {
        // 실측(시도 17곳 × 3회)에서 504 가 5건이었다. 3회 모두 실패한 시도는 없어 한 번이면 대부분 회복된다.
        String ok = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":[
                  {"pm10Value":"40","pm25Value":"20","khaiGrade":"2"}
                ]}}}""";
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        Optional<AirQuality> result = flakyClient(ok, calls).realtimeBySido("서울");

        assertTrue(result.isPresent(), "한 번 실패해도 재시도로 회복돼야 한다");
        assertEquals(2, calls.get(), "재시도는 한 번만 — 죽어 있는 동안 지연이 곱해지면 안 된다");
    }

    @Test
    void 성공_코드에_측정소가_0건이면_빈_결과다() {
        // 실측에서 광주·전남이 3회 모두 이랬다. 504 와 결과는 같지만 원인이 다르다.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":[]}}}""";

        assertTrue(client(body).realtimeBySido("전남").isEmpty());
    }

    @Test
    void 실패_코드는_빈_결과다() {
        String body = """
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},
                 "body":{"items":[]}}}""";

        assertTrue(client(body).realtimeBySido("서울").isEmpty());
    }
}
