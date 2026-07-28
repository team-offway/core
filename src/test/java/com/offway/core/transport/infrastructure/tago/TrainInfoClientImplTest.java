package com.offway.core.transport.infrastructure.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.infrastructure.tago.dto.TrainAvailability;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** TrainInfoClientImpl stub 테스트 — 외부 HTTP 경계만 ExchangeFunction 으로 격리. 응답 스키마는 TAGO 실호출로 검증된 형태. */
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

        TrainAvailability result = client(body).fastestTrain("NAT010000", "NAT013271", DATE);

        TrainAvailability.Available available = assertInstanceOf(TrainAvailability.Available.class, result);
        assertEquals("KTX", available.fastest().trainType()); // 100분 < 무궁화 210분
        assertEquals(100, available.fastest().durationMinutes());
    }

    @Test
    void 열차편이_하나면_item이_객체로_와도_처리한다() {
        // data.go.kr 은 결과가 하나면 item 을 배열이 아니라 객체 하나로 내려준다.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
                  {"traingradename":"KTX","depplandtime":"20260501070000","arrplandtime":"20260501084000"}
                }}}}""";

        TrainAvailability result = client(body).fastestTrain("NAT010000", "NAT013271", DATE);

        TrainAvailability.Available available = assertInstanceOf(TrainAvailability.Available.class, result);
        assertEquals("KTX", available.fastest().trainType());
        assertEquals(100, available.fastest().durationMinutes());
    }

    @Test
    void 해당_날짜_미운행이면_NoServiceOnDate다() {
        // 미운행이면 items 가 빈 문자열로 온다.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":""}}}""";

        TrainAvailability result = client(body).fastestTrain("NAT010000", "NAT013271", DATE);

        assertInstanceOf(TrainAvailability.NoServiceOnDate.class, result);
    }

    @Test
    void 편은_있는데_전부_파싱실패면_Unavailable이다() {
        // 미운행(빈 items)과 달리, 편이 있는데 시각 결측이면 스키마 변경 신호 → 잘못된 "없음" 안내 대신 Unavailable.
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                  {"traingradename":"KTX","depplandtime":"","arrplandtime":""}
                ]}}}}""";

        assertInstanceOf(
                TrainAvailability.Unavailable.class, client(body).fastestTrain("NAT010000", "NAT013271", DATE));
    }

    @Test
    void 비정상_resultCode는_Unavailable이다() {
        String body = """
                {"response":{"header":{"resultCode":"99"},"body":{}}}""";

        assertInstanceOf(TrainAvailability.Unavailable.class, client(body).fastestTrain("A", "B", DATE));
    }

    @Test
    void 키가_없으면_호출하지_않고_Unavailable을_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 TAGO 열차 호출이 일어났다");
                })
                .build();

        assertInstanceOf(
                TrainAvailability.Unavailable.class,
                new TrainInfoClientImpl(neverCalled, NO_KEY).fastestTrain("NAT010000", "NAT013271", DATE));
    }
}
