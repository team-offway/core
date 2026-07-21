package com.offway.core.leave.infrastructure.holiday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.leave.domain.HolidayException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * HolidayClientImpl stub 통합 테스트.
 *
 * <p>외부 HTTP 경계만 {@link ExchangeFunction} 으로 격리한다 — MockWebServer 없이 응답·에러·지연을 프로그래머블하게 준다.
 */
class HolidayClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

    /** 주어진 응답을 그대로 돌려주는 WebClient 를 만든다. */
    private static WebClient stubbing(ClientResponse response) {
        ExchangeFunction stub = request -> Mono.just(response);
        return WebClient.builder().exchangeFunction(stub).build();
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    @Test
    void 공휴일_응답을_날짜집합으로_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                "body":{"items":{"item":[
                  {"locdate":20260101,"dateName":"1월1일","isHoliday":"Y"},
                  {"locdate":20260301,"dateName":"삼일절","isHoliday":"Y"}
                ]},"numOfRows":10,"pageNo":1,"totalCount":2}}}""";
        HolidayClient client = new HolidayClientImpl(stubbing(json(body)), WITH_KEY);

        Set<LocalDate> holidays = client.getHolidays(2026, 1);

        assertEquals(Set.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)), holidays);
    }

    @Test
    void 키가_없으면_호출하지_않고_빈집합을_돌려준다() {
        // stub 이 throw 하도록 두어 "호출 안 함"을 증명한다.
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 외부 호출이 일어났다");
                })
                .build();
        HolidayClient client = new HolidayClientImpl(neverCalled, NO_KEY);

        assertTrue(client.getHolidays(2026, 1).isEmpty());
    }

    @Test
    void 공휴일이_한_건이면_item이_단일객체로_와도_파싱한다() {
        // data.go.kr 함정: 1건일 때 item 이 배열이 아니라 단일 객체로 온다.
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":
                  {"locdate":20260815,"dateName":"광복절","isHoliday":"Y"}
                },"totalCount":1}}}""";
        HolidayClient client = new HolidayClientImpl(stubbing(json(body)), WITH_KEY);

        assertEquals(Set.of(LocalDate.of(2026, 8, 15)), client.getHolidays(2026, 8));
    }

    @Test
    void 데이터가_없으면_items가_빈문자열이어도_빈집합을_돌려준다() {
        // data.go.kr 함정: 결과 없으면 items 가 "" (빈 문자열).
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":"","totalCount":0}}}""";
        HolidayClient client = new HolidayClientImpl(stubbing(json(body)), WITH_KEY);

        assertTrue(client.getHolidays(2026, 4).isEmpty());
    }

    @Test
    void isHoliday가_N인_날은_제외한다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},
                "body":{"items":{"item":[
                  {"locdate":20260101,"dateName":"1월1일","isHoliday":"Y"},
                  {"locdate":20260210,"dateName":"평일기념일","isHoliday":"N"}
                ]},"totalCount":2}}}""";
        HolidayClient client = new HolidayClientImpl(stubbing(json(body)), WITH_KEY);

        assertEquals(Set.of(LocalDate.of(2026, 1, 1)), client.getHolidays(2026, 1));
    }

    @Test
    void HTTP_에러_응답은_502_예외로_올린다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"down\"}")
                .build();
        HolidayClient client = new HolidayClientImpl(stubbing(error), WITH_KEY);

        HolidayException ex = assertThrows(HolidayException.class, () -> client.getHolidays(2026, 1));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.httpStatus());
    }

    @Test
    void 응답이_깨진_JSON이면_502_예외로_올린다() {
        HolidayClient client = new HolidayClientImpl(stubbing(json("<html>not json</html>")), WITH_KEY);

        assertThrows(HolidayException.class, () -> client.getHolidays(2026, 1));
    }

    @Test
    void 응답_지연이_timeout을_넘으면_502_예외로_올린다() {
        // 응답을 TIMEOUT(6s) 보다 오래 지연시켜 timeout 분기를 친다.
        ExchangeFunction slow = request -> Mono.delay(Duration.ofSeconds(30))
                .map(t -> json("{}"));
        HolidayClient client = new HolidayClientImpl(
                WebClient.builder().exchangeFunction(slow).build(), WITH_KEY);

        assertThrows(HolidayException.class, () -> client.getHolidays(2026, 1));
    }
}
