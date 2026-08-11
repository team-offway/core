package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.time.Duration;
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

    /** 이 테스트들은 집계 예산 제약을 검증하지 않는다 - 자체 timeout(20초)보다 길게 줘서 영향을 없앤다. */
    private static final Duration AMPLE = Duration.ofMinutes(1);

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
        return new TourDataLabClientImpl(stubbing(json(body)), WITH_KEY, new NoOpCallRecorder());
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

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 3, AMPLE);

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
        TourDataLabClient client = new TourDataLabClientImpl(neverCalled, NO_KEY, new NoOpCallRecorder());

        assertTrue(client.findRegionVisitors(FROM, TO, 1, 10, AMPLE).items().isEmpty());
    }

    @Test
    void 알수없는_방문자구분은_건너뛴다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"2","touNum":"100.0","baseYmd":"20260601"},
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"9","touNum":"5.0","baseYmd":"20260601"}
                ]},"totalCount":2}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 10, AMPLE);

        assertEquals(1, result.items().size()); // touDivCd=9 는 건너뜀
        assertEquals(VisitorType.DOMESTIC, result.items().get(0).type());
    }

    @Test
    void 날짜나_방문자수_형식이_잘못된_항목은_그_한건만_건너뛴다() {
        // 한 건의 이상 데이터가 전체 요청을 502로 터뜨리거나 집계를 오염시키면 안 된다.
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"2","touNum":"100.0","baseYmd":"20260601"},
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"2","touNum":"100.0","baseYmd":"엉터리날짜"},
                  {"signguCode":"11110","signguNm":"종로구","touDivCd":"2","touNum":"숫자아님","baseYmd":"20260601"}
                ]},"totalCount":3}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 10, AMPLE);

        assertEquals(1, result.items().size()); // 정상 1건만, 날짜·숫자 형식 오류는 스킵
        assertEquals(100.0, result.items().get(0).count(), 0.001);
    }

    @Test
    void 결과가_1건이면_item이_단일객체로_와도_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":
                  {"signguCode":"46890","signguNm":"완도군","touDivCd":"2","touNum":"12345.0","baseYmd":"20260601"}
                },"totalCount":1}}}""";

        TourVisitorResult result = client(body).findRegionVisitors(FROM, TO, 1, 10, AMPLE);

        assertEquals(1, result.items().size());
        assertEquals("완도군", result.items().get(0).signguName());
    }

    @Test
    void 결과가_없으면_items가_빈문자열이어도_빈결과를_돌려준다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        assertTrue(client(body).findRegionVisitors(FROM, TO, 1, 10, AMPLE).items().isEmpty());
    }

    @Test
    void resultCode가_성공이_아니면_502로_올린다() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS"},
                "body":""}}""";

        assertThrows(TourApiException.class, () -> client(body).findRegionVisitors(FROM, TO, 1, 10, AMPLE));
    }

    @Test
    void 남은_예산이_자체_timeout보다_짧으면_그만큼만_기다린다() {
        // 응답이 오지 않는 서버 — 대기 시간이 순수하게 timeout 설정에서만 나온다.
        WebClient hanging = WebClient.builder().exchangeFunction(request -> Mono.never()).build();
        TourDataLabClient client = new TourDataLabClientImpl(hanging, WITH_KEY, new NoOpCallRecorder());

        long startedAt = System.nanoTime();
        assertThrows(TourApiException.class,
                () -> client.findRegionVisitors(FROM, TO, 1, 10, Duration.ofMillis(300)));
        Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

        // 자체 timeout(20초)이 아니라 넘겨받은 300ms 를 따랐다는 것. 집계의 전체 상한이 마지막 한 건까지
        // 실제로 내려가는지가 이 단언의 핵심이다 — 안 내려가면 상한이 20초씩 초과된다.
        assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0,
                "남은 예산 300ms 를 넘겼는데 " + waited.toMillis() + "ms 기다렸다 — 자체 timeout 이 그대로 걸렸다");
    }

    @Test
    void HTTP_에러_응답은_502로_올린다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"down\"}")
                .build();
        TourDataLabClient client = new TourDataLabClientImpl(stubbing(error), WITH_KEY, new NoOpCallRecorder());

        TourApiException ex =
                assertThrows(TourApiException.class, () -> client.findRegionVisitors(FROM, TO, 1, 10, AMPLE));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.httpStatus());
    }
}
