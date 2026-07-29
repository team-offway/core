package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** BusArrivalClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. */
class BusArrivalClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);
    private static final BusStop STOP = new BusStop("GMB165", "정선터미널", 32020, 37.3801, 128.6604);

    private static BusArrivalClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> Mono.just(response)).build();
        return new BusArrivalClientImpl(webClient, WITH_KEY);
    }

    @Test
    void 도착예정_버스를_빠른_순으로_돌려준다() {
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"routeno":"3","routetp":"일반버스","arrtime":900,"arrprevstationcnt":7},
                  {"routeno":"1","routetp":"농어촌버스","arrtime":180,"arrprevstationcnt":2}
                ]}}}}""";

        BusArrivalStatus result = client(body).arrivalsAt(STOP);

        BusArrivalStatus.Arriving arriving = assertInstanceOf(BusArrivalStatus.Arriving.class, result);
        assertEquals("1", arriving.soonest().routeNo()); // 응답 순서와 무관하게 빠른 순 정렬
        assertEquals(3, arriving.soonest().arrivalMinutes());
    }

    @Test
    void 오는_버스가_없으면_NoBusSoon이다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":""}}}""";

        assertInstanceOf(BusArrivalStatus.NoBusSoon.class, client(body).arrivalsAt(STOP));
    }

    @Test
    void 항목은_있는데_전부_파싱실패면_Unavailable이다() {
        // 도착시간 결측은 스키마 변경 신호다. 잘못된 "오는 버스 없음" 안내를 막는다.
        String body =
                """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"routeno":"1","routetp":"농어촌버스"}
                ]}}}}""";

        assertInstanceOf(BusArrivalStatus.Unavailable.class, client(body).arrivalsAt(STOP));
    }

    @Test
    void 비정상_resultCode는_Unavailable이다() {
        String body = """
                {"response":{"header":{"resultCode":"99"},"body":{}}}""";

        assertInstanceOf(BusArrivalStatus.Unavailable.class, client(body).arrivalsAt(STOP));
    }

    @Test
    void 키가_없으면_호출하지_않고_Unavailable을_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TAGO 도착정보 호출이 일어났다");
                })
                .build();

        assertInstanceOf(
                BusArrivalStatus.Unavailable.class, new BusArrivalClientImpl(neverCalled, NO_KEY).arrivalsAt(STOP));
    }
}
