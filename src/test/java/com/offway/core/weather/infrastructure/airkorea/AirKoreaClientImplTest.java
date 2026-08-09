package com.offway.core.weather.infrastructure.airkorea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    /**
     * 첫 호출은 {@code firstStatus}, 그 다음은 정상 — 간헐 장애를 재현한다.
     *
     * <p>첫 응답을 <b>실제 상태코드</b>로 만든다. 예전에는 {@code RuntimeException("SERVICETIMEOUT_ERROR")}
     * 를 던졌는데, 그러면 "504 라서 재시도했다" 가 아니라 "아무 예외나 재시도한다" 를 검증하게 된다 —
     * 필터를 넣어도 테스트가 안 깨지므로 정책을 지켜주지 못한다.
     *
     * <p>실측(2026-08-10, 51회)에서 {@code SERVICETIMEOUT_ERROR} 는 전부 HTTP 504 로 왔다.
     */
    private static AirKoreaClient flakyClient(
            HttpStatus firstStatus, String okBody, java.util.concurrent.atomic.AtomicInteger calls) {
        return new AirKoreaClientImpl(
                WebClient.builder()
                        .exchangeFunction(request -> {
                            if (calls.incrementAndGet() == 1) {
                                return Mono.just(ClientResponse.create(firstStatus)
                                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .body("{\"OpenAPI_ServiceResponse\":{\"cmmMsgHeader\":"
                                                + "{\"errMsg\":\"SERVICETIMEOUT_ERROR\"}}}")
                                        .build());
                            }
                            return Mono.just(ClientResponse.create(HttpStatus.OK)
                                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .body(okBody)
                                    .build());
                        })
                        .build(),
                WITH_KEY);
    }

    private static final String OK_BODY = """
            {"response":{"header":{"resultCode":"00"},"body":{"items":[
              {"pm10Value":"40","pm25Value":"20","khaiGrade":"2"}
            ]}}}""";

    @Test
    void 간헐_504는_한_번_다시_걸어_회복한다() {
        // 실측(2026-08-10, 시도 17곳 × 3회 = 51회)에서 504 가 16건(31%)이었다. 흩어져 나므로 한 번이면 대부분 회복된다.
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        Optional<AirQuality> result =
                flakyClient(HttpStatus.GATEWAY_TIMEOUT, OK_BODY, calls).realtimeBySido("서울");

        assertTrue(result.isPresent(), "504 한 번은 재시도로 회복돼야 한다");
        assertEquals(2, calls.get(), "재시도는 한 번만 — 죽어 있는 동안 지연이 곱해지면 안 된다");
    }

    @ParameterizedTest(name = "{0} 은 다시 걸지 않는다")
    @EnumSource(
            value = HttpStatus.class,
            names = {"UNAUTHORIZED", "BAD_REQUEST", "INTERNAL_SERVER_ERROR", "TOO_MANY_REQUESTS"})
    void 게이트웨이_timeout이_아니면_다시_걸지_않는다(HttpStatus status) {
        // 다시 걸어도 같은 답이 오는 실패다. 재시도하면 지연만 두 배가 되고, 429 는 오히려 더 밀어붙이는 꼴이다.
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        Optional<AirQuality> result = flakyClient(status, OK_BODY, calls).realtimeBySido("서울");

        assertTrue(result.isEmpty(), "재시도하지 않으므로 첫 실패가 그대로 결과다");
        assertEquals(1, calls.get(), "504 가 아니면 한 번만 부른다");
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
