package com.offway.core.trip.infrastructure.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalKeyState;
import com.offway.core.common.external.FallbackKeyAlert;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * data.go.kr 한도가 말랐을 때 보조 키로 넘어가는가(#596).
 *
 * <h2>한도 소진은 예외가 아니다</h2>
 *
 * <p>게이트웨이가 <b>HTTP 200</b> 에 거절 envelope 을 실어 준다. 그래서 예외 기반으로만 폴백을 짜면
 * 이 경우가 통째로 새어 나간다 — 정작 가장 흔한 실패인데도. 이 테스트가 그 모양을 고정한다.
 */
class TourApiFallbackKeyTest {

    private static final String PRIMARY = "primary-key";
    private static final String FALLBACK = "fallback-key";

    /** 한도가 마르면 오는 응답 — 200 인데 response 가 없다. */
    private static final String QUOTA_EXCEEDED = """
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
               "errMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR",
               "returnAuthMsg":"서비스 요청제한횟수 초과",
               "returnReasonCode":"22"}}}""";

    /** 보조 키가 이 서비스에 활용신청되지 않았을 때 — 한도가 아닌 다른 거절이다. */
    private static final String NOT_REGISTERED = """
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
               "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
               "returnAuthMsg":"등록되지 않은 서비스키",
               "returnReasonCode":"30"}}}""";

    private static final String OK = """
            {"response":{"header":{"resultCode":"0000"},
             "body":{"totalCount":1,"items":{"item":[
               {"contentid":"1","title":"테스트","mapx":"127.0","mapy":"37.0"}]}}}}""";

    /** 나간 요청의 serviceKey 를 순서대로 붙잡는다. */
    private static final class Calls {

        private final List<String> keys = new ArrayList<>();
        private final List<ClientResponse> responses;
        private int index;

        private Calls(ClientResponse... responses) {
            this.responses = List.of(responses);
        }

        private WebClient webClient() {
            return WebClient.builder().exchangeFunction(request -> {
                keys.add(serviceKeyOf(request));
                ClientResponse next = responses.get(Math.min(index, responses.size() - 1));
                index++;
                return Mono.just(next);
            }).build();
        }

        private static String serviceKeyOf(ClientRequest request) {
            for (String pair : request.url().getQuery().split("&")) {
                if (pair.startsWith("serviceKey=")) {
                    return pair.substring("serviceKey=".length());
                }
            }
            return null;
        }
    }

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

    private static ClientResponse status(HttpStatus status) {
        return ClientResponse.create(status).body("").build();
    }

    private static ExternalApiProperties keys(String primary, String fallback) {
        return ExternalApiProperties.builder()
                .dataGoKr(new ExternalApiProperties.DataGoKr(primary, fallback))
                .build();
    }

    private static TourApiClient client(Calls calls, ExternalApiProperties props,
            ExternalApiCallRecorder recorder, List<String> alerts) {
        return client(calls, props, recorder, alerts, new ExternalKeyState());
    }

    private static TourApiClient client(Calls calls, ExternalApiProperties props,
            ExternalApiCallRecorder recorder, List<String> alerts, ExternalKeyState state) {
        return new TourApiClientImpl(calls.webClient(), props, recorder, new FallbackKeyAlert(alerts::add), state);
    }

    @Test
    void 한도가_마르면_보조_키로_다시_묻는다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), json(OK));
        List<String> alerts = new ArrayList<>();

        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10);

        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys, "두 번째 호출이 보조 키로 나가야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키 → 보조 키")), alerts.toString());
    }

    @Test
    void 보조_키_호출은_한도_집계에_넣지_않는다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), json(OK));
        CountingRecorder recorder = new CountingRecorder();

        client(calls, keys(PRIMARY, FALLBACK), recorder, new ArrayList<>())
                .findByArea(34, 1, null, 10);

        assertEquals(List.of(ExternalApi.TOUR_API), recorder.recorded,
                "주 키 1회만 세어야 한다 — 나간 호출은 2회지만 하나는 다른 키다");
    }

    /** 평소 응답에는 폴백이 끼어들지 않는다 — 키를 늘려 한도를 두 배로 쓰자는 장치가 아니다. */
    @Test
    void 정상_응답이면_보조_키를_부르지_않는다() {
        Calls calls = new Calls(json(OK));
        List<String> alerts = new ArrayList<>();

        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10);

        assertEquals(List.of(PRIMARY), calls.keys);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void 보조_키까지_마르면_알린다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), json(QUOTA_EXCEEDED));
        List<String> alerts = new ArrayList<>();

        try {
            client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                    .findByArea(34, 1, null, 10);
        } catch (RuntimeException expected) {
            // 둘 다 말랐으면 조회는 실패한다(502). 여기서 잠그는 것은 그 사실을 알렸는가다.
        }

        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
    }

    @Test
    void 보조_키가_없으면_설정_누락을_알린다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED));
        List<String> alerts = new ArrayList<>();

        try {
            client(calls, keys(PRIMARY, null), new CountingRecorder(), alerts)
                    .findByArea(34, 1, null, 10);
        } catch (RuntimeException expected) {
            // 조회는 실패한다. 알림이 이유를 말해 주는지만 본다.
        }

        assertEquals(List.of(PRIMARY), calls.keys, "보조 키가 없으면 호출이 한 번뿐이어야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("보조 키가 설정돼 있지 않습니다")), alerts.toString());
    }

    /**
     * <b>보조 키로는 한 번만 간다</b> — 429 가 와도 재시도하지 않는다.
     *
     * <p>주 키 호출은 429 에 두 번 더 재시도한다. 그 재시도를 보조 키 호출에도 그대로 두면 "한 번만 더" 가
     * 최대 세 번이 되고, 그만큼 보조 키 한도를 태운다.
     */
    @Test
    void 보조_키가_사백이십구를_받아도_다시_묻지_않는다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), status(HttpStatus.TOO_MANY_REQUESTS));
        List<String> alerts = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10));

        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys, "보조 키 호출이 한 번이어야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
    }

    /**
     * <b>한도가 아닌 거절도 "받아 줬다" 가 아니다.</b>
     *
     * <p>활용신청은 서비스마다 따로라, 보조 키가 이 서비스에 등록되지 않은 경우가 실제로 있을 수 있다.
     * 그때 "화면은 정상입니다" 를 보내면 알림이 거짓말을 한다.
     */
    @Test
    void 보조_키가_미등록으로_거절되면_전환이_아니라_실패로_알린다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), json(NOT_REGISTERED));
        List<String> alerts = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10));

        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
        assertTrue(alerts.stream().noneMatch(a -> a.contains("주 키 → 보조 키")), "거절에 정상 알림을 냈다: " + alerts);
    }

    /**
     * <b>보조 키로 도는 날, 그 호출이 예외로 끝나도 알린다.</b>
     *
     * <p>주 키를 "오늘은 마름" 으로 기억한 뒤라 그날 남은 요청은 전부 보조 키로 먼저 간다. 거기서 예외를
     * 알리지 않으면 <b>그날 내내 알림 없이 502</b> 다 — 이 폴백이 막으려던 조용한 실패 그대로다.
     */
    @Test
    void 보조_키로_도는_날_호출이_예외로_끝나도_알린다() {
        ExternalKeyState state = new ExternalKeyState();
        state.markPrimaryExhausted(ExternalApi.TOUR_API);
        Calls calls = new Calls(status(HttpStatus.INTERNAL_SERVER_ERROR));
        List<String> alerts = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts, state)
                .findByArea(34, 1, null, 10));

        assertEquals(List.of(FALLBACK), calls.keys);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
    }

    /**
     * <b>게이트웨이 거절이 아닌 실패 응답도 "받아 줬다" 가 아니다.</b>
     *
     * <p>{@code resultCode} 가 성공이 아니면 뒤의 파서가 502 로 끝낸다. 그런데 전환 판정이 게이트웨이 거절만 보면
     * 그 사이에 "화면은 정상입니다" 가 먼저 나간다 — 판정 기준을 파서와 같게 맞췄는지를 잠근다.
     */
    @Test
    void 보조_키_응답의_결과코드가_실패면_전환이_아니라_실패로_알린다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED),
                json("{\"response\":{\"header\":{\"resultCode\":\"99\",\"resultMsg\":\"ERROR\"}}}"));
        List<String> alerts = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10));

        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
        assertTrue(alerts.stream().noneMatch(a -> a.contains("주 키 → 보조 키")), "실패 응답에 정상 알림을 냈다: " + alerts);
    }

    /** 보조 키로 도는 날 첫 호출이 <b>200 으로 실패</b>해도 알린다 — 예외가 아니라 조용히 지나가는 경로다. */
    @Test
    void 보조_키로_도는_날_첫_호출이_실패_응답이어도_알린다() {
        ExternalKeyState state = new ExternalKeyState();
        state.markPrimaryExhausted(ExternalApi.TOUR_API);
        Calls calls = new Calls(json(NOT_REGISTERED));
        List<String> alerts = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts, state)
                .findByArea(34, 1, null, 10));

        assertEquals(List.of(FALLBACK), calls.keys);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("주 키·보조 키 모두 실패")), alerts.toString());
    }
}
