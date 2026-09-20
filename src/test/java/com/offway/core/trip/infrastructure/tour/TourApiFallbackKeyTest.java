package com.offway.core.trip.infrastructure.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
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
            super(null, null);
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

    private static ExternalApiProperties keys(String primary, String fallback) {
        return ExternalApiProperties.builder()
                .dataGoKr(new ExternalApiProperties.DataGoKr(primary, fallback))
                .build();
    }

    private static TourApiClient client(Calls calls, ExternalApiProperties props,
            ExternalApiCallRecorder recorder, List<String> alerts) {
        return new TourApiClientImpl(calls.webClient(), props, recorder, new FallbackKeyAlert(alerts::add));
    }

    @Test
    void 한도가_마르면_보조_키로_다시_묻는다() {
        Calls calls = new Calls(json(QUOTA_EXCEEDED), json(OK));
        List<String> alerts = new ArrayList<>();

        client(calls, keys(PRIMARY, FALLBACK), new CountingRecorder(), alerts)
                .findByArea(34, 1, null, 10);

        assertEquals(List.of(PRIMARY, FALLBACK), calls.keys, "두 번째 호출이 보조 키로 나가야 한다");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("보조 키로 넘어갔습니다")), alerts.toString());
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
        assertTrue(alerts.stream().anyMatch(a -> a.contains("모두 실패")), alerts.toString());
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
}
