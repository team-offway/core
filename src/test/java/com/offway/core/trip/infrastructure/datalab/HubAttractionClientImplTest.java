package com.offway.core.trip.infrastructure.datalab;

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
        return new HubAttractionClientImpl(stubbing(response), WITH_KEY);
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
    void 성공코드가_아니면_빈_목록이다() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},
                "body":{"items":{"item":[{"hubTatsNm":"공산성","hubRank":"1"}]}}}}""";

        assertTrue(client(body).findByRegion(GONGJU, MONTH, 30).isEmpty());
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
                stubbing(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()), NO_KEY);

        assertTrue(client.findByRegion(GONGJU, MONTH, 30).isEmpty());
    }

    @Test
    void 응답이_깨지면_조회불가로_올린다() {
        // 빈 목록으로 삼키면 "미발행" 과 구분이 안 돼 이전 값을 덮을지 판단할 수 없다.
        assertThrows(TourApiException.class, () -> client("깨진 JSON").findByRegion(GONGJU, MONTH, 30));
    }
}
