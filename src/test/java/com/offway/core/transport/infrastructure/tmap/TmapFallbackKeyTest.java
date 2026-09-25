package com.offway.core.transport.infrastructure.tmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalKeyState;
import com.offway.core.common.external.FallbackKeyAlert;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 주 키가 실패했을 때 <b>보조 키로 한 번 더</b> 가는가(#596).
 *
 * <h2>왜 이 테스트가 필요한가</h2>
 *
 * <p>TMAP 경유지 최적화는 한도가 50 이라 자차 코스 17건이면 마른다. 마르면 상위가 직선거리 정렬로
 * 떨어지는데 <b>응답은 200</b> 이다 — 순서가 틀린 코스가 정상처럼 나간다. 심사처럼 다시 할 수 없는
 * 자리에서는 그 조용함이 곧 결과가 된다.
 *
 * <p>그래서 여기서 잠그는 것은 "폴백이 돈다" 가 아니라 <b>"다른 키로 다시 물었다"</b> 와
 * <b>"주 키 집계를 오염시키지 않았다"</b> 두 가지다.
 */
class TmapFallbackKeyTest {

    private static final String PRIMARY = "primary-key";
    private static final String FALLBACK = "fallback-key";

    private static final Coordinate SEOUL = new Coordinate(37.5665, 126.9780);
    private static final Coordinate BUSAN = new Coordinate(35.1796, 129.0756);

    private static final String ROUTE_OK = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"totalDistance":12000,"totalTime":1500}}
            ]}""";

    /**
     * 경유지 최적화가 받아 준 모양 — 점 넷(출발·경유 둘·도착)이면 경유지 둘이 {@code B1}·{@code B2} 로 온다.
     *
     * <p>예전 픽스처({@code {"properties":{"totalTime":1}}})는 <b>파서를 통과하지 못하는 모양</b>이었다.
     * 그런데도 "보조 키로 넘어갔습니다" 를 기대하고 있었다 — 쓸 수 없는 응답에 정상 알림을 내던 버그를
     * 테스트가 그대로 굳히고 있던 셈이다.
     */
    private static final String OPTIMIZE_OK = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point"},"properties":{"pointType":"B1","viaPointId":"2"}},
              {"type":"Feature","geometry":{"type":"Point"},"properties":{"pointType":"B2","viaPointId":"1"}}
            ]}""";

    /** 나간 요청의 appKey 를 순서대로 붙잡고, 준비된 응답을 차례로 돌려준다. */
    private static final class Calls {

        private final List<String> keys = new ArrayList<>();
        private final List<ClientResponse> responses;
        private int index;

        private Calls(ClientResponse... responses) {
            this.responses = List.of(responses);
        }

        private WebClient webClient() {
            return WebClient.builder().exchangeFunction(request -> {
                keys.add(request.headers().getFirst("appKey"));
                ClientResponse next = responses.get(Math.min(index, responses.size() - 1));
                index++;
                return Mono.just(next);
            }).build();
        }
    }

    /** 무엇을 몇 번 셌는지 붙잡는 기록기 — 보조 키 호출이 섞이는지 본다. */
    private static final class CountingRecorder extends ExternalApiCallRecorder {

        private final List<ExternalApi> recorded = new ArrayList<>();

        private CountingRecorder() {
            super(null, null, new ExternalKeyState());
        }

        @Override
        public void record(ExternalApi api) {
            recorded.add(api);
        }
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static ClientResponse failure() {
        return ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).body("").build();
    }

    /** TMAP 이 좌표를 거절한 모양 — 도로 링크가 없다(#335). */
    private static ClientResponse rejected() {
        return ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"code\":\"1100\"}}")
                .build();
    }

    private static ExternalApiProperties keys(String primary, String fallback) {
        return ExternalApiProperties.builder()
                .tmap(new ExternalApiProperties.Tmap(primary, fallback))
                .build();
    }

    private static TmapClient client(Calls calls, ExternalApiProperties props,
            ExternalApiCallRecorder recorder, List<String> alerts) {
        return client(calls, props, recorder, alerts, new ExternalKeyState());
    }

    private static TmapClient client(Calls calls, ExternalApiProperties props,
            ExternalApiCallRecorder recorder, List<String> alerts, ExternalKeyState state) {
        return new TmapClientImpl(calls.webClient(), props, recorder,
                new FallbackKeyAlert(alerts::add), state);
    }

    private static List<Coordinate> fourPoints() {
        return List.of(SEOUL, BUSAN, SEOUL, BUSAN);
    }

    @Test
    void 경유지_최적화가_실패하면_보조_키로_다시_묻는다() {
        Calls calls = new Calls(failure(), json(OPTIMIZE_OK));
        List<String> alerts = new ArrayList<>();

        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys, "두 번째 호출이 보조 키로 나가야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키 → 보조 키")),
                "보조 키로 넘어간 사실을 알려야 한다: " + alerts);
    }

    /**
     * <b>보조 키 호출은 주 키 한도 집계에 넣지 않는다.</b>
     *
     * <p>넣으면 "우리가 이 API 를 얼마나 쓰나" 를 말하는 숫자가 틀린다 — 그 집계가 심사 자료로 나간다.
     */
    @Test
    void 보조_키_호출은_한도_집계에_넣지_않는다() {
        Calls calls = new Calls(failure(), json(OPTIMIZE_OK));
        CountingRecorder recorder = new CountingRecorder();

        client(calls, keys(PRIMARY, FALLBACK), recorder, new ArrayList<>())
                .optimizeCarOrder(fourPoints());

        assertEquals(List.of(ExternalApi.TMAP_WAYPOINT), recorder.recorded,
                "주 키 1회만 세어야 한다 — 나간 호출은 2회지만 하나는 다른 키다");
    }

    @Test
    void 보조_키가_없으면_다시_묻지_않고_설정_누락을_알린다() {
        Calls calls = new Calls(failure());
        List<String> alerts = new ArrayList<>();

        Optional<List<Integer>> order = client(calls, keys(PRIMARY, null), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertTrue(order.isEmpty());
        assertEquals(List.of(PRIMARY), calls.keys, "보조 키가 없으면 호출이 한 번뿐이어야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("보조 키가 설정돼 있지 않습니다")), alerts.toString());
    }

    @Test
    void 둘_다_실패하면_알리고_빈_값을_준다() {
        Calls calls = new Calls(failure(), failure());
        List<String> alerts = new ArrayList<>();

        Optional<List<Integer>> order = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertTrue(order.isEmpty(), "둘 다 실패하면 상위가 직선거리로 떨어진다");
        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
    }

    /**
     * <b>좌표 탓이면 보조 키로 다시 묻지 않는다.</b>
     *
     * <p>도로 링크가 없는 지점은 어느 키로 물어도 없다. 재시도하면 보조 키 한도만 태우고 답은 같다 —
     * 정작 한도가 필요한 순간에 그만큼이 없다.
     */
    @Test
    void 좌표_거절은_보조_키를_태우지_않는다() {
        Calls calls = new Calls(rejected());
        List<String> alerts = new ArrayList<>();

        CarRouteResult result = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .carRoute(SEOUL, BUSAN);

        assertInstanceOf(CarRouteResult.Rejected.class, result);
        assertEquals(List.of(PRIMARY), calls.keys, "좌표 거절에는 보조 키를 쓰지 않는다");
        assertTrue(alerts.isEmpty(), "알릴 일이 아니다 — 한도 문제가 아니라 그 지점의 문제다");
    }

    @Test
    void 경로_조회도_보조_키로_이어진다() {
        Calls calls = new Calls(failure(), json(ROUTE_OK));
        List<String> alerts = new ArrayList<>();

        CarRouteResult result = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .carRoute(SEOUL, BUSAN);

        assertEquals(25, assertInstanceOf(CarRouteResult.Found.class, result).route().durationMinutes());
        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys);
    }

    /** 평소에는 보조 키를 안 쓴다 — 키를 늘려 한도를 두 배로 쓰자는 장치가 아니다. */
    @Test
    void 주_키가_성공하면_보조_키를_부르지_않는다() {
        Calls calls = new Calls(json(ROUTE_OK));
        List<String> alerts = new ArrayList<>();

        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts).carRoute(SEOUL, BUSAN);

        assertEquals(List.of(PRIMARY), calls.keys);
        assertTrue(alerts.isEmpty());
    }

    /**
     * <b>상태가 "보조 키" 면 그쪽을 먼저 쓴다.</b>
     *
     * <p>TMAP 자신은 이 표시를 달지 않는다 — 429 가 일일 한도인지 초당 유량 제한인지 못 가르기
     * 때문이다(클래스 주석 참고). 표시가 붙는 경로는 data.go.kr 쪽이고, 여기서는 <b>붙었을 때
     * 키 선택이 그것을 따르는지</b>만 본다.
     */
    @Test
    void 상태가_보조_키면_그쪽을_먼저_쓴다() {
        ExternalKeyState state = new ExternalKeyState();
        state.markPrimaryExhausted(ExternalApi.TMAP_WAYPOINT);

        Calls calls = new Calls(json(OPTIMIZE_OK));
        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), new ArrayList<>(), state)
                .optimizeCarOrder(fourPoints());

        assertEquals(List.of(FALLBACK), calls.keys);
    }

    /** 보조 키로 도는 날에는 <b>첫 호출도 보조 키</b>라 주 키 집계가 더 오르지 않아야 한다. */
    @Test
    void 보조_키로_도는_날에는_주_키_집계가_오르지_않는다() {
        ExternalKeyState state = new ExternalKeyState();
        state.markPrimaryExhausted(ExternalApi.TMAP_WAYPOINT);
        CountingRecorder recorder = new CountingRecorder();

        client(new Calls(json(OPTIMIZE_OK)),
                keys(PRIMARY, FALLBACK), recorder, new ArrayList<>(), state)
                .optimizeCarOrder(fourPoints());

        assertTrue(recorder.recorded.isEmpty(), "보조 키 호출은 주 키 한도에 세지 않는다");
    }

    /**
     * <b>TMAP 은 429 를 한도로 단정하지 않는다.</b>
     *
     * <p>429 가 일일 한도인지 초당 유량 제한인지 구분할 수 없다 — TourAPI 쪽은 같은 429 를 "잠시 뒤
     * 재시도" 로 다룬다. 한도로 읽고 기억을 달면 일시적인 제한 한 번이 그날 내내 보조 키를 쓰게 만들고,
     * 정작 진짜 마르는 순간에 보조 키가 닳아 있다.
     */
    @Test
    void 사백이십구를_받아도_하루치_기억을_남기지_않는다() {
        ExternalKeyState state = new ExternalKeyState();

        client(new Calls(failure(), json(OPTIMIZE_OK)),
                keys(PRIMARY, FALLBACK), new CountingRecorder(), new ArrayList<>(), state)
                .optimizeCarOrder(fourPoints());

        assertFalse(state.usingFallback(ExternalApi.TMAP_WAYPOINT),
                "429 로 하루를 고정하면 일시적 제한 한 번에 보조 키를 다 태운다");
    }

    /**
     * <b>보조 키 응답을 쓸 수 없으면 "전환" 이 아니라 "둘 다 실패" 로 알린다.</b>
     *
     * <p>응답이 왔다는 것만 보고 "화면은 정상입니다" 를 보내면, 사용자는 직선거리 정렬을 보는데 운영 채널에는
     * 정상이라고 남는다 — 알림이 가장 믿기 어려운 순간에 거짓말을 한다.
     */
    @Test
    void 보조_키_응답을_해석하지_못하면_전환이_아니라_실패로_알린다() {
        Calls calls = new Calls(failure(), json("{\"unexpected\":true}"));
        List<String> alerts = new ArrayList<>();

        Optional<List<Integer>> order = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertTrue(order.isEmpty());
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
        assertTrue(alerts.stream().noneMatch(a -> a.contains("주 키 → 보조 키")), "쓸 수 없는 응답에 정상 알림을 냈다: " + alerts);
    }

    @Test
    void 보조_키가_받아_주면_순서를_돌려주고_전환을_알린다() {
        Calls calls = new Calls(failure(), json(OPTIMIZE_OK));
        List<String> alerts = new ArrayList<>();

        Optional<List<Integer>> order = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertEquals(Optional.of(List.of(0, 2, 1, 3)), order);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키 → 보조 키")), alerts.toString());
    }

    /** 경유지 최적화도 경로 탐색과 같은 규칙이다 — 한도 50 짜리라 한 번이 더 비싸다. */
    @Test
    void 경유지_최적화도_좌표_거절이면_보조_키를_태우지_않는다() {
        Calls calls = new Calls(rejected());
        List<String> alerts = new ArrayList<>();

        Optional<List<Integer>> order = client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .optimizeCarOrder(fourPoints());

        assertTrue(order.isEmpty(), "상위가 직선거리 정렬로 떨어진다");
        assertEquals(List.of(PRIMARY), calls.keys, "좌표 거절에는 보조 키를 쓰지 않는다");
        assertTrue(alerts.isEmpty(), "알릴 일이 아니다 — 한도 문제가 아니라 그 지점의 문제다");
    }
}
