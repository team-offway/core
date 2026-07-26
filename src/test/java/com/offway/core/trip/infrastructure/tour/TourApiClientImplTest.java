package com.offway.core.trip.infrastructure.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * TourApiClientImpl stub 통합 테스트. 외부 HTTP 경계만 {@link ExchangeFunction} 으로 격리한다.
 */
class TourApiClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

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

    private static TourApiClient client(String body) {
        return new TourApiClientImpl(stubbing(json(body)), WITH_KEY);
    }

    @Test
    void 지역기반_목록을_POI로_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"contentid":"126508","contenttypeid":"12","lclsSystm1":"NA","title":"가사동백숲해변","addr1":"전남 완도군",
                   "mapx":"126.9277","mapy":"34.3698","firstimage":"http://img/1.jpg","tel":"061-1"},
                  {"contentid":"200","contenttypeid":"39","lclsSystm1":"FD","title":"완도 전복집","addr1":null,
                   "mapx":"126.7","mapy":"34.3","firstimage":"","tel":""}
                ]},"numOfRows":10,"pageNo":1,"totalCount":38}}}""";

        TourPoiResult result = client(body).findByArea(38, 1, null, 10);

        assertEquals(38, result.totalCount());
        assertEquals(2, result.items().size());
        TourPoi first = result.items().get(0);
        assertEquals("126508", first.contentId());
        assertEquals(12, first.contentTypeId());
        assertEquals("NA", first.lclsSystm1());
        assertEquals("FD", result.items().get(1).lclsSystm1());
        assertEquals("가사동백숲해변", first.title());
        assertEquals(34.3698, first.lat(), 0.0001); // mapy
        assertEquals(126.9277, first.lng(), 0.0001); // mapx
        assertEquals("http://img/1.jpg", first.firstImage());
        assertNull(result.items().get(1).address()); // JSON 명시적 null → null(문자열 "null" 아님)
        assertNull(result.items().get(1).firstImage()); // 빈 문자열 → null
        assertNull(result.items().get(1).tel());
    }

    @Test
    void 키가_없으면_호출하지_않고_빈결과를_돌려준다() {
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 외부 호출이 일어났다");
                })
                .build();
        TourApiClient client = new TourApiClientImpl(neverCalled, NO_KEY);

        assertTrue(client.findByArea(1, null, null, 10).items().isEmpty());
    }

    @Test
    void 결과_1건이면_item이_단일객체로_와도_파싱한다() {
        // data.go.kr 함정: 1건일 때 item 이 배열이 아니라 단일 객체.
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":
                  {"contentid":"1","contenttypeid":"12","title":"완도타워","mapx":"126.7","mapy":"34.3"}
                },"totalCount":1}}}""";

        TourPoiResult result = client(body).findByArea(38, null, 12, 10);

        assertEquals(1, result.items().size());
        assertEquals("완도타워", result.items().get(0).title());
    }

    @Test
    void 결과가_없으면_items가_빈문자열이어도_빈결과를_돌려준다() {
        // data.go.kr 함정: 결과 없으면 items 가 "" (빈 문자열).
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        TourPoiResult result = client(body).findByArea(1, null, null, 10);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.totalCount());
    }

    @Test
    void resultCode가_성공이_아니면_빈결과가_아니라_502로_올린다() {
        // 키·쿼터 오류가 "결과 없음"으로 둔갑하면 추천이 조용히 비어버린다.
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS"},
                "body":""}}""";

        assertThrows(TourApiException.class, () -> client(body).findByArea(1, null, null, 10));
    }

    @Test
    void HTTP_에러_응답은_502로_올린다() {
        ClientResponse error = ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"down\"}")
                .build();
        TourApiClient client = new TourApiClientImpl(stubbing(error), WITH_KEY);

        TourApiException ex = assertThrows(TourApiException.class, () -> client.findByArea(1, null, null, 10));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.httpStatus());
    }

    @Test
    void 응답이_깨진_JSON이면_502로_올린다() {
        assertThrows(TourApiException.class, () -> client("<html>not json</html>").findByArea(1, null, null, 10));
    }

    @Test
    void 위치기반_목록도_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"contentid":"7","contenttypeid":"12","title":"근처 관광지","mapx":"127.0","mapy":"37.5"}
                ]},"totalCount":1}}}""";

        TourPoiResult result = client(body).findByLocation(37.5, 127.0, 5000, 12, 10);

        assertEquals(1, result.items().size());
        assertEquals("근처 관광지", result.items().get(0).title());
    }

    @Test
    void 소개정보에서_운영시간과_휴무일을_뽑는다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"contentid":"126508","usetime":"09:00~18:00","restdate":"연중무휴"}
                ]},"totalCount":1}}}""";

        Optional<TourIntro> intro = client(body).findIntro("126508", 12);

        assertTrue(intro.isPresent());
        assertEquals("09:00~18:00", intro.get().useTime());
        assertEquals("연중무휴", intro.get().restDate());
    }

    @Test
    void 소개정보가_없으면_빈Optional을_돌려준다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        assertTrue(client(body).findIntro("999", 12).isEmpty());
    }
}
