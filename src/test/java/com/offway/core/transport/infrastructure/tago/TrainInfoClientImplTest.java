package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** TrainInfoClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. 응답 스키마는 TAGO 문서 기준(실호출 전파 시 검증). */
class TrainInfoClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);
    private static final LocalDate DATE = LocalDate.of(2026, 5, 1);

    private static TrainInfoClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
        return new TrainInfoClientImpl(webClient, WITH_KEY);
    }

    @Test
    void 여러_열차편_중_소요시간이_가장_짧은_것을_고른다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"traingradename":"무궁화","depplandtime":"20260501060000","arrplandtime":"20260501093000"},
                  {"traingradename":"KTX","depplandtime":"20260501070000","arrplandtime":"20260501084000"}
                ]}}}}""";

        Optional<TrainLeg> leg = client(body).fastestTrain("NAT010000", "NAT013271", DATE);

        assertTrue(leg.isPresent());
        assertEquals("KTX", leg.get().trainType()); // 100분 < 무궁화 210분
        assertEquals(100, leg.get().durationMinutes());
    }

    @Test
    void 해당_날짜_미운행이면_빈결과다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":""}}}""";

        assertTrue(client(body).fastestTrain("NAT010000", "NAT013271", DATE).isEmpty());
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TAGO 열차 호출이 일어났다");
                })
                .build();

        assertTrue(new TrainInfoClientImpl(neverCalled, NO_KEY)
                .fastestTrain("NAT010000", "NAT013271", DATE)
                .isEmpty());
    }
}
