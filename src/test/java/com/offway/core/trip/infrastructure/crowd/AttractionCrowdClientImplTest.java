package com.offway.core.trip.infrastructure.crowd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.NoOpCallRecorder;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@code TatsCnctrRateService/tatsCnctrRatedList} 응답 파싱(#565). 실호출로 확인한 필드를 그대로 쓴다.
 *
 * <p>data.go.kr 의 함정들을 여기서 막는다 — 결과 없음이 <b>빈 문자열</b> items 로 오고, 1건이면 item 이
 * 배열이 아니라 <b>단일 객체</b>다. 둘 다 성공 코드로 와서 예외가 안 나므로 파싱이 조용히 틀린다.
 */
class AttractionCrowdClientImplTest {

    private static final ExternalApiProperties WITH_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr("test-key"), null);
    private static final ExternalApiProperties NO_KEY =
            new ExternalApiProperties(new ExternalApiProperties.DataGoKr(null), null);

    private static final String TAEAN = "44825";
    private static final Duration WAIT = Duration.ofSeconds(5);

    private static WebClient stubbing(String body) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
        ExchangeFunction stub = request -> Mono.just(response);
        return WebClient.builder().exchangeFunction(stub).build();
    }

    private static AttractionCrowdClient client(String body) {
        return new AttractionCrowdClientImpl(stubbing(body), WITH_KEY, new NoOpCallRecorder());
    }

    @Test
    void 관광지명과_날짜와_집중률을_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"baseYmd":"20260910","areaCd":"44","areaNm":"충청남도",
                   "signguCd":"44825","signguNm":"태안군","tAtsNm":"가의도","cnctrRate":"45.17"},
                  {"baseYmd":"20260912","areaCd":"44","areaNm":"충청남도",
                   "signguCd":"44825","signguNm":"태안군","tAtsNm":"가의도","cnctrRate":"88.10"}
                ]},"numOfRows":2,"pageNo":1,"totalCount":2}}}""";

        List<AttractionCrowd> items = client(body).findByRegion(TAEAN, WAIT);

        assertEquals(2, items.size());
        assertEquals("가의도", items.getFirst().attractionName());
        assertEquals(LocalDate.of(2026, 9, 10), items.getFirst().date());
        assertEquals(45.17, items.getFirst().rate());
        assertEquals(88.10, items.get(1).rate());
    }

    /** 한 건이면 item 이 배열이 아니라 단일 객체다. */
    @Test
    void 단일_객체로_와도_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":
                  {"baseYmd":"20260910","tAtsNm":"안면도쥬라기박물관","cnctrRate":"12.5"}
                },"numOfRows":1,"pageNo":1,"totalCount":1}}}""";

        List<AttractionCrowd> items = client(body).findByRegion(TAEAN, WAIT);

        assertEquals(1, items.size());
        assertEquals("안면도쥬라기박물관", items.getFirst().attractionName());
    }

    /** 결과 없음은 items 가 빈 문자열이다 — 예보가 없는 16곳이 이렇게 온다. */
    @Test
    void 예보가_없는_지역은_빈_목록이다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}""";

        assertTrue(client(body).findByRegion(TAEAN, WAIT).isEmpty());
    }

    /**
     * <b>행은 왔는데 이름을 하나도 못 읽으면 던진다.</b> 필드명이 바뀌면 조용히 0건이 되는데, 그러면
     * 그 지역을 빈 예보로 갈아 끼워 칩이 통째로 사라진다 — 축제가 정확히 그렇게 죽었다(#506).
     */
    @Test
    void 이름을_하나도_못_읽으면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"baseYmd":"20260910","attractionName":"가의도","rate":"45.17"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion(TAEAN, WAIT));
    }

    /**
     * <b>잘린 응답을 성공으로 돌려주지 않는다.</b> 뒷부분이 빠진 채 저장하면 그 관광지들이 "예보가 없는
     * 곳" 이 되어 칩이 조용히 사라진다.
     */
    @Test
    void 받은_행이_전체와_다르면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"baseYmd":"20260910","tAtsNm":"가의도","cnctrRate":"45.17"}
                ]},"numOfRows":1,"pageNo":1,"totalCount":2910}}}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion(TAEAN, WAIT));
    }

    @Test
    void 성공_코드가_아니면_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS"},
                "body":{}}}""";

        assertThrows(TourApiException.class, () -> client(body).findByRegion(TAEAN, WAIT));
    }

    /**
     * 날짜나 집중률을 못 읽은 행은 <b>그 행만</b> 버린다.
     *
     * <p>이름은 읽혔으므로 필드명이 틀린 것은 아니다. 값 하나가 깨졌다고 그 지역을 통째로 버리면
     * 멀쩡한 나머지 예보까지 잃는다.
     */
    @Test
    void 날짜나_값을_못_읽은_행만_버린다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[
                  {"baseYmd":"20260910","tAtsNm":"가의도","cnctrRate":"45.17"},
                  {"baseYmd":"","tAtsNm":"날짜없음","cnctrRate":"50.0"},
                  {"baseYmd":"20260911","tAtsNm":"값이상","cnctrRate":"알수없음"}
                ]},"numOfRows":3,"pageNo":1,"totalCount":3}}}""";

        List<AttractionCrowd> items = client(body).findByRegion(TAEAN, WAIT);

        assertEquals(1, items.size());
        assertEquals("가의도", items.getFirst().attractionName());
    }

    /** 키가 없으면 부르지 않는다 — 로컬 실행성(키 없이도 부팅된다). */
    @Test
    void 키가_없으면_빈_목록이다() {
        AttractionCrowdClient client =
                new AttractionCrowdClientImpl(stubbing("{}"), NO_KEY, new NoOpCallRecorder());

        assertTrue(client.findByRegion(TAEAN, WAIT).isEmpty());
    }
}
