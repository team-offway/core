package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusArrival;
import com.offway.core.transport.domain.BusArrivalStatus;
import com.offway.core.transport.domain.BusStop;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TAGO 버스도착정보 adapter — {@code ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList}(정류소별 도착예정).
 * data.go.kr 서비스라 같은 serviceKey 를 쓴다.
 *
 * <p>경로 주의: 버스 서비스는 base 가 {@code ...InqireService} 이고 오퍼레이션이 <b>소문자</b>다. 열차({@code TrainInfo}
 * base + 대문자 G)와 규칙이 다르다.
 */
@Slf4j
@Component
class BusArrivalClientImpl implements BusArrivalClient {

    private static final String URL =
            "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /** 한 정류소에 곧 오는 버스는 많아야 수십 대다. */
    private static final int ROWS = 30;

    private static final String CITY_CODE = "cityCode";
    private static final String NODE_ID = "nodeId";

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    BusArrivalClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public BusArrivalStatus arrivalsAt(BusStop stop) {
        if (!props.dataGoKr().hasKey()) {
            return new BusArrivalStatus.Unavailable();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam(TagoQuery.SERVICE_KEY, props.dataGoKr().serviceKey())
                .queryParam(TagoQuery.RESPONSE_TYPE, TagoQuery.RESPONSE_TYPE_JSON)
                .queryParam(TagoQuery.NUM_OF_ROWS, ROWS)
                .queryParam(TagoQuery.PAGE_NO, TagoQuery.FIRST_PAGE)
                .queryParam(CITY_CODE, stop.cityCode())
                .queryParam(NODE_ID, stop.nodeId());
        try {
            return parse(call(builder));
        } catch (Exception e) {
            log.warn("TAGO 버스도착정보 조회 실패 — 조회 불가 처리 cause={}", e.getClass().getSimpleName());
            return new BusArrivalStatus.Unavailable();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — TourApiClientImpl 과 동일 규약.
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.BUS_ARRIVAL);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private BusArrivalStatus parse(String body) throws Exception {
        return switch (TagoItems.parse(body, objectMapper)) {
            case TagoItems.Items(List<JsonNode> nodes) -> toStatus(nodes);
            case TagoItems.Empty ignored -> new BusArrivalStatus.NoBusSoon();
            case TagoItems.Failed ignored -> new BusArrivalStatus.Unavailable();
        };
    }

    private static BusArrivalStatus toStatus(List<JsonNode> nodes) {
        List<BusArrival> arrivals = nodes.stream()
                .map(BusArrivalClientImpl::toArrival)
                .flatMap(Optional::stream)
                .toList();
        // 항목은 있는데 전부 파싱 실패면 "오는 버스 없음" 이 아니라 스키마 변경 신호다.
        return arrivals.isEmpty() ? new BusArrivalStatus.Unavailable() : new BusArrivalStatus.Arriving(arrivals);
    }

    private static Optional<BusArrival> toArrival(JsonNode node) {
        String routeNo = node.path("routeno").asText(null);
        JsonNode arrivalSeconds = node.path("arrtime");
        if (routeNo == null || routeNo.isBlank() || !arrivalSeconds.isNumber() || arrivalSeconds.asInt() < 0) {
            return Optional.empty(); // 부분 결측 방어 — 이 항목만 건너뛴다
        }
        return Optional.of(new BusArrival(
                routeNo,
                node.path("routetp").asText(null),
                arrivalSeconds.asInt(),
                Math.max(0, node.path("arrprevstationcnt").asInt())));
    }
}
