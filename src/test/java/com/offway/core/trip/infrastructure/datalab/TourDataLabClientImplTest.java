package com.offway.core.trip.infrastructure.datalab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * TourDataLabClientImpl stub 통합 테스트. 실제 locgoRegnVisitrDDList 응답 포맷을 그대로 쓴다(실호출로 확인한 필드).
 */
class TourDataLabClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 7);

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

    private static TourDataLabClient client(String body) {
        return new TourDataLabClientImpl(stubbing(json(body)), WITH_KEY);
    }

    @Test
    void 기초_시군구_방문자수를_구분별로_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"signguCode":"11110","signguNm":"종로구","daywkDivCd":"1","daywkDivNm":"월요일",
                   "touDivCd":"1","touDivNm":"현지인(a)","touNum":"197743.0","baseYmd":"20260601"},
                  {"signguCode":"11110","signguNm":"종로구","daywkDivCd":"1","daywkDivNm":"월요일",
                   "touDivCd":"2","touDivNm":"외지인(b)","touNum":"360713.5","baseYmd":"20260601"},
                  {"signguCode":"11110","signguNm":"종로구","daywkDivCd":"1","daywkDivNm":"월요일",
                   "touDivCd":"3","touDivNm":"외국인(c)","touNum":"1000.0","baseYmd":"20260601"}
                ]},"numOfRows":3,"pageNo":1,"totalCount":5628}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 3);

        assertEquals(5628, result.totalCount());
        assertEquals(3, result.items().size());
        RegionVisitor domestic = result.items().get(1);
        assertEquals("종로구", domestic.signguName());
        assertEquals(VisitorType.DOMESTIC, domestic.type());
        assertEquals(360713.5, domestic.count(), 0.001);
        assertEquals(LocalDate.of(2026, 6, 1), domestic.baseDate());
        assertTrue(domestic.type().isTourist()); // 외지인 = 관광객
        assertFalse(result.items().get(0).type().isTourist()); // 현지인 = 제외 대상
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 외부 호출이 일어났다");
                })
                .build();
        TourDataLabClient client = new TourDataLabClientImpl(neverCalled, NO_KEY);

        assertTrue(client.findRegionVisitors(FROM, TO, 1, 10).items().isEmpty());
    }

    @Test
    void 알수없는_방문자구분은_건너뛴다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"2","touNum":"100.0","baseYmd":"20260601"},
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"9","touNum":"5.0","baseYmd":"20260601"}
                ]},"totalCount":2}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 10);

        assertEquals(1, result.items().size()); // touDivCd=9 는 건너뜀
        assertEquals(VisitorType.DOMESTIC, result.items().get(0).type());
    }

    @Test
    void 결과가_1건이면_item이_단일객체로_와도_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":
                  {"signguCode":"46890","signguNm":"완도군","touDivCd":"2","touNum":"12345.0","baseYmd":"20260601"}
                },"totalCount":1}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 10);

        assertEquals(1, result.items().size());
        assertEquals("완도군", result.items().get(0).signguName());
    }

    @Test
    void 결과가_없으면_items가_빈문자열이어도_빈결과를_돌려준다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        assertTrue(client(body).findRegionVisitors(FROM, TO, 1, 10).items().isEmpty());
    }

    @Test
    void resultCode가_성공이_아니면_502로_올린다() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS"},
                "body":""}}""";

        assertThrows(TourApiException.class, () -> client(body).findRegionVisitors(FROM, TO, 1, 10));
    }

    @Test
    void HTTP_에러_응답은_502로_올린다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"down\"}")
                .build();
        TourDataLabClient client = new TourDataLabClientImpl(stubbing(error), WITH_KEY);

        TourApiException ex =
                assertThrows(TourApiException.class, () -> client.findRegionVisitors(FROM, TO, 1, 10));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.httpStatus());
    }
}
