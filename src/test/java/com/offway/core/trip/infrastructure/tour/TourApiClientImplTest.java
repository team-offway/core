package com.offway.core.trip.infrastructure.tour;

import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.NoOpCallRecorder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
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

    /** 최초 호출 1회 + 구현의 재시도 2회. 구현 상수가 줄면 여기가 먼저 깨져야 한다. */
    private static final int ATTEMPTS_WITH_RETRIES = 3;

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
        return new TourApiClientImpl(stubbing(json(body)), WITH_KEY, new NoOpCallRecorder());
    }

    /** 호출마다 다음 응답을 돌려주고 호출 횟수를 센다 — 재시도가 실제로 다시 걸리는지 보려면 필요하다. */
    private static final class Sequence {
        private final java.util.List<ClientResponse> responses;
        private final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();

        private Sequence(ClientResponse... responses) {
            this.responses = java.util.List.of(responses);
        }

        private WebClient webClient() {
            ExchangeFunction stub = request -> {
                int index = Math.min(calls.getAndIncrement(), responses.size() - 1);
                return Mono.just(responses.get(index));
            };
            return WebClient.builder().exchangeFunction(stub).build();
        }

        private int calls() {
            return calls.get();
        }
    }

    /** 몇 번 세었는지만 붙잡는 기록기 — "실제로 나간 호출" 과 "우리가 센 호출" 을 맞대보려면 필요하다. */
    private static final class CountingCallRecorder extends ExternalApiCallRecorder {

        private final java.util.concurrent.atomic.AtomicInteger counted =
                new java.util.concurrent.atomic.AtomicInteger();

        // NoOpCallRecorder 와 같은 이유로 저장소·알림 없이 만든다 — 여기서 세는 것은 횟수뿐이라
        // record 를 통째로 덮어써 원본 구현에 닿지 않는다.
        private CountingCallRecorder() {
            super(null, null);
        }

        @Override
        public void record(ExternalApi api) {
            counted.incrementAndGet();
        }

        private int counted() {
            return counted.get();
        }
    }

    private static ClientResponse tooManyRequests() {
        return ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build();
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
        TourApiClient client = new TourApiClientImpl(neverCalled, NO_KEY, new NoOpCallRecorder());

        assertTrue(client.findByArea(1, null, null, 10).items().isEmpty());
    }

    @Test
    void 키가_없으면_상세조회는_빈결과가_아니라_502로_올린다() {
        // 빈결과로 돌려주면 PoiDetailService 가 "장소 없음(404)"으로 오인한다 — 조회 불가(502)로 분리.
        WebClient neverCalled = WebClient.builder()
                .exchangeFunction(request -> {
                    throw new AssertionError("키가 없는데 외부 호출이 일어났다");
                })
                .build();
        TourApiClient client = new TourApiClientImpl(neverCalled, NO_KEY, new NoOpCallRecorder());

        TourApiException detailEx = assertThrows(TourApiException.class, () -> client.findDetail("126508"));
        assertEquals(HttpStatus.BAD_GATEWAY, detailEx.httpStatus());
        TourApiException introEx = assertThrows(TourApiException.class, () -> client.findIntro("126508", 12));
        assertEquals(HttpStatus.BAD_GATEWAY, introEx.httpStatus());
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
        TourApiClient client = new TourApiClientImpl(stubbing(error), WITH_KEY, new NoOpCallRecorder());

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

    @Test
    void 공통상세를_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"contentid":"126508","contenttypeid":"12","title":"완도타워","addr1":"전남 완도군","tel":"061-1",
                   "mapx":"126.7","mapy":"34.3","firstimage":"http://img/1.jpg","overview":"전망대 소개"}
                ]},"totalCount":1}}}""";

        Optional<TourPoiDetail> detail = client(body).findDetail("126508");

        assertTrue(detail.isPresent());
        assertEquals("완도타워", detail.get().title());
        assertEquals(12, detail.get().contentTypeId());
        assertEquals("전남 완도군", detail.get().address());
        assertEquals("전망대 소개", detail.get().overview());
        assertEquals(34.3, detail.get().lat(), 0.0001); // mapy
    }

    @Test
    void 공통상세가_없으면_빈Optional을_돌려준다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        assertTrue(client(body).findDetail("999").isEmpty());
    }

    @Test
    void 무장애정보를_파싱한다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[
                  {"contentid":"126508","wheelchair":"대여가능","restroom":"장애인 화장실 있음",
                   "audioguide":"음성안내 있음","exit":"","parking":"   "}
                ]},"totalCount":1}}}""";

        Optional<TourAccessibility> accessibility = client(body).findAccessibility("126508");

        assertTrue(accessibility.isPresent());
        assertEquals("대여가능", accessibility.get().wheelchair());
        // 빈/공백 필드는 편의로 접히지 않는다(exit·parking 제외 → 3건).
        assertEquals(3, accessibility.get().toPoiAccessibility().features().size());
    }

    @Test
    void 무장애정보가_없으면_빈Optional을_돌려준다() {
        String body = """
                {"response":{"header":{"resultCode":"0000"},"body":{"items":"","totalCount":0}}}""";

        assertTrue(client(body).findAccessibility("999").isEmpty());
    }

    @Test
    void 초당_한도에_걸리면_다시_걸어_성공한다() {
        // 429 는 "지금은 많으니 잠시 뒤" 라는 뜻이다. 즉시 포기하면 그 지역 콘텐츠가 빈 채로 캐시된다(#191).
        String ok = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[{"contentid":"1","contenttypeid":"12","title":"갑사"}]},"totalCount":1}}}""";
        Sequence sequence = new Sequence(tooManyRequests(), json(ok));
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, new NoOpCallRecorder());

        assertEquals(1, client.findByArea(34, 1, null, 10).items().size());
        assertEquals(2, sequence.calls(), "429 를 받으면 한 번 더 걸어야 한다");
    }

    @Test
    void 재시도를_다_써도_429면_조회불가로_올린다() {
        // 무한정 매달리지 않는다 — 상한을 넘으면 degrade 하고 그 사실을 로그로 남긴다.
        Sequence sequence = new Sequence(tooManyRequests());
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, new NoOpCallRecorder());

        assertThrows(TourApiException.class, () -> client.findByArea(34, 1, null, 10));
        // 최초 1회 + 재시도 2회. 정확히 세지 않으면 재시도 횟수가 줄어도 이 테스트가 통과해
        // 상한이 조용히 바뀐다.
        assertEquals(ATTEMPTS_WITH_RETRIES, sequence.calls(), "실제=" + sequence.calls());
    }

    @Test
    void 다시_건_호출도_사용량에_센다() {
        // 재시도는 실제로 나가는 호출이라 제공기관 쪽 한도를 그만큼 깎는다. 그런데 세지 않으면 우리 카운터가
        // 낮게 나와 "아직 여유 있다" 로 읽힌다 — 하필 429 가 도는 그때, 즉 한도가 마르고 있는 그때 틀린다.
        String ok = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[{"contentid":"1","contenttypeid":"12","title":"갑사"}]},"totalCount":1}}}""";
        Sequence sequence = new Sequence(tooManyRequests(), tooManyRequests(), json(ok));
        CountingCallRecorder recorder = new CountingCallRecorder();
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, recorder);

        client.findByArea(34, 1, null, 10);

        assertEquals(ATTEMPTS_WITH_RETRIES, sequence.calls(), "실제로 나간 호출");
        assertEquals(sequence.calls(), recorder.counted(), "우리가 센 호출");
    }

    @Test
    void 재시도를_다_쓰고_실패해도_나간_만큼_센다() {
        // 실패로 끝나도 호출은 이미 나갔다. 여기서 안 세면 한도가 마를수록 카운터가 더 크게 어긋난다.
        Sequence sequence = new Sequence(tooManyRequests());
        CountingCallRecorder recorder = new CountingCallRecorder();
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, recorder);

        assertThrows(TourApiException.class, () -> client.findByArea(34, 1, null, 10));

        assertEquals(sequence.calls(), recorder.counted(), "우리가 센 호출");
    }

    @Test
    void 재시도가_없으면_한_번만_센다() {
        // 재시도를 세느라 평상시 호출을 두 번 세면, 고치려던 것과 반대 방향으로 틀린다.
        String ok = """
                {"response":{"header":{"resultCode":"0000"},
                "body":{"items":{"item":[{"contentid":"1","contenttypeid":"12","title":"갑사"}]},"totalCount":1}}}""";
        Sequence sequence = new Sequence(json(ok));
        CountingCallRecorder recorder = new CountingCallRecorder();
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, recorder);

        client.findByArea(34, 1, null, 10);

        assertEquals(1, sequence.calls());
        assertEquals(1, recorder.counted());
    }

    @Test
    void 서버오류는_다시_걸지_않는다() {
        // 5xx·timeout 은 이미 느린 상황이라 다시 걸면 지연만 곱해진다. 429 에만 재시도를 건다.
        Sequence sequence = new Sequence(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        TourApiClient client = new TourApiClientImpl(sequence.webClient(), WITH_KEY, new NoOpCallRecorder());

        assertThrows(TourApiException.class, () -> client.findByArea(34, 1, null, 10));
        assertEquals(1, sequence.calls(), "5xx 는 재시도 대상이 아니다");
    }
}
