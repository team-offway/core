package com.offway.core.trip.infrastructure.datalab;

import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@code LocgoHubTarService1/areaBasedList1} 응답 파싱. 실호출로 확인한 필드를 그대로 쓴다.
 *
 * <p>data.go.kr 의 함정들을 여기서 막는다 — 결과 없음이 <b>빈 문자열</b> items 로 오고, 1건이면 item 이
 * 배열이 아니라 <b>단일 객체</b>다. 둘 다 성공 코드로 와서 예외가 안 나므로 파싱이 조용히 틀린다.
 */
class HubAttractionClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

    private static final String GONGJU = "44150";
    private static final YearMonth MONTH = YearMonth.of(2026, 6);

    private static WebClient stubbing(ClientResponse response) {
        ExchangeFunction stub = request -> Mono.just(response);
        return WebClient.builder().exchangeFunction(stub).build();
    }

    private static HubAttractionClient client(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        return new HubAttractionClientImpl(stubbing(response), WITH_KEY, new NoOpCallRecorder());
    }

    @Test
    void 중심_관광지를_순위와_분류와_좌표까지_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"baseYm":"202606","mapX":"127.125369166840000","mapY":"36.464479161580700",
                   "areaCd":"44","areaNm":"충청남도","signguCd":"44150","signguNm":"공주시",
                   "hubTatsCd":"52498f93bb5c1825ebf59bb810a10ec8","hubTatsNm":"공산성",
                   "hubCtgryLclsNm":"관광지","hubCtgryMclsNm":"역사관광","hubRank":"1"},
                  {"baseYm":"202606","mapX":"127.112231438257000","mapY":"36.465534520323500",
                   "areaCd":"44","areaNm":"충청남도","signguCd":"44150","signguNm":"공주시",
                   "hubTatsCd":"56b9eb3be6fb3c7f4144531ec516bf53","hubTatsNm":"국립공주박물관",
                   "hubCtgryLclsNm":"관광지","hubCtgryMclsNm":"문화관광","hubRank":"2"}
                ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}""";

        List<HubAttractionItem> items = client(body).findByRegion(GONGJU, MONTH, 30);

        assertEquals(2, items.size());
        HubAttractionItem first = items.getFirst();
        assertEquals(1, first.rank());
        assertEquals("공산성", first.name());
        assertEquals("관광지", first.categoryLarge());
        assertEquals("역사관광", first.categoryMedium());
        // 데이터랩은 mapX 가 경도, mapY 가 위도다. 뒤집으면 지도 핀이 엉뚱한 데 찍힌다.
        assertEquals(36.4644791615807, first.lat());
        assertEquals(127.12536916684, first.lng());
    }

    @Test
    void 한_건이면_item_이_단일_객체로_온다() {
        // 배열만 가정하면 1건짜리 지자체가 통째로 빈다.
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":
                  {"hubTatsCd":"c1","hubTatsNm":"공산성","hubCtgryLclsNm":"관광지",
                   "hubCtgryMclsNm":"역사관광","hubRank":"1","mapX":"127.1","mapY":"36.4"}
                },"totalCount":1}}}""";

        List<HubAttractionItem> items = client(body).findByRegion(GONGJU, MONTH, 30);

        assertEquals(1, items.size());
        assertEquals("공산성", items.getFirst().name());
    }

    @Test
    void 결과가_없으면_items_가_빈_문자열로_온다() {
        // 미발행 월이 이렇게 온다. 성공 코드라 예외가 안 나므로 여기서 빈 목록으로 받아야 한다.
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}""";

        assertTrue(client(body).findByRegion(GONGJU, MONTH, 30).isEmpty());
    }

    @Test
    void 성공코드가_아니면_조회불가로_올린다() {
        // 빈 목록으로 돌려주면 "미발행" 과 구분되지 않는다 — 할당량 초과가 발행 지연으로 읽혀 발행월 탐색이
        // 이전 달들을 헛되이 순회하고, 호출자 집계에도 실패가 아니라 빈 응답으로 잡힌다.
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},
                "body":{"items":{"item":[{"hubTatsNm":"공산성","hubRank":"1"}]}}}}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion(GONGJU, MONTH, 30));
    }

    @Test
    void 좌표가_없거나_깨져도_나머지는_살린다() {
        // 좌표만 없다고 그 지역 1위를 버리면 랭킹이 통째로 밀린다. 좌표는 비우고 이름·순위는 남긴다.
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"hubTatsCd":"c1","hubTatsNm":"공산성","hubCtgryLclsNm":"관광지","hubRank":"1",
                   "mapX":"","mapY":"좌표없음"}
                ]}}}}""";

        HubAttractionItem item = client(body).findByRegion(GONGJU, MONTH, 30).getFirst();

        assertEquals("공산성", item.name());
        assertNull(item.lat());
        assertNull(item.lng());
    }

    @Test
    void 키가_없으면_외부를_부르지_않고_빈_목록이다() {
        // 로컬 실행성 — 키 없이도 부팅·동작이 막히지 않는다.
        HubAttractionClient client = new HubAttractionClientImpl(
                stubbing(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()), NO_KEY, new NoOpCallRecorder());

        assertTrue(client.findByRegion(GONGJU, MONTH, 30).isEmpty());
    }

    @Test
    void 응답이_깨지면_조회불가로_올린다() {
        // 빈 목록으로 삼키면 "미발행" 과 구분이 안 돼 이전 값을 덮을지 판단할 수 없다.
        assertThrows(TourApiException.class, () -> client("깨진 JSON").findByRegion(GONGJU, MONTH, 30));
    }

    @Test
    void 오류는_response_래퍼_없이_최상위로_온다() {
        // 실측: pageNo 를 빠뜨리면 이 모양이 온다. 래퍼만 보면 코드가 빈 문자열이 돼 "결과 없음" 과 구분이 안 된다.
        String body = """
                {"responseTime":"2026-08-09T17:24:22.692","resultCode":"11",
                 "resultMsg":"NO_MANDATORY_REQUEST_PARAMETERS_ERROR1(pageNo)"}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion(GONGJU, MONTH, 30));
    }

    @Test
    void 법정동_코드가_짧으면_조회불가로_올린다() {
        // 그냥 두면 substring 이 StringIndexOutOfBounds 를 던지는데, 그건 호출자의 지역별 격리를 뚫고
        // 올라가 89곳 루프를 통째로 멈춘다. 같은 실패 경로에 태워 그 지역만 건너뛰게 한다.
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":""}}}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion("4", MONTH, 30));
        assertThrows(TourApiException.class, () -> client(body).findByRegion(null, MONTH, 30));
    }

    @Test
    void 필수_값이_빠진_항목은_건너뛰고_나머지를_준다() {
        // path().asInt() 는 없으면 0, asText() 는 빈 문자열을 준다 — 그대로 넘기면 한참 뒤 toEntity() 에서
        // IllegalArgumentException 이 터지고, 그 자리는 호출자가 외부 실패로 잡는 경계 밖이다.
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"hubTatsCd":"c1","hubTatsNm":"공산성","hubRank":"1"},
                  {"hubTatsNm":"코드없음","hubRank":"2"},
                  {"hubTatsCd":"c3","hubRank":"3"},
                  {"hubTatsCd":"c4","hubTatsNm":"순위없음"}
                ]}}}}""";

        List<HubAttractionItem> items = client(body).findByRegion(GONGJU, MONTH, 30);

        assertEquals(1, items.size(), "온전한 1건만 남는다");
        assertEquals("공산성", items.getFirst().name());
    }
}
