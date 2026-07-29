package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TAGO 버스정류소정보 adapter — {@code BusSttnInfoInqireService/getCrdntPrxmtSttnList}(좌표 기반 근접 정류소).
 * data.go.kr 서비스라 같은 serviceKey 를 쓴다.
 *
 * <p>경로 주의: 버스 서비스는 base 가 {@code ...InqireService} 이고 오퍼레이션이 <b>소문자</b>({@code
 * getCrdntPrxmtSttnList})다. 열차({@code TrainInfo} base + 대문자 G)와 규칙이 다르다 — 틀린 조합은 게이트웨이가
 * {@code 404 "API not found"} 를 준다.
 */
@Slf4j
@Component
class BusStopClientImpl implements BusStopClient {

    private static final String URL =
            "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /** 접근성 판정에는 가장 가까운 몇 곳이면 충분하다 — 전량을 받아 파싱할 이유가 없다. */
    private static final int ROWS = 20;

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    BusStopClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public BusStopAccess nearbyStops(double lat, double lng) {
        if (!props.dataGoKr().hasKey()) {
            return new BusStopAccess.Unavailable();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("_type", "json")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("gpsLati", lat)
                .queryParam("gpsLong", lng);
        try {
            return parse(call(builder));
        } catch (Exception e) {
            log.warn("TAGO 버스정류소 조회 실패 — 조회 불가 처리 cause={}", e.getClass().getSimpleName());
            return new BusStopAccess.Unavailable();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 hex 라 재인코딩해도 안전.
        URI uri = builder.encode().build().toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private BusStopAccess parse(String body) throws Exception {
        return switch (TagoItems.parse(body, objectMapper)) {
            case TagoItems.Items(List<JsonNode> nodes) -> toAccess(nodes);
            case TagoItems.Empty ignored -> new BusStopAccess.NoStopNearby();
            case TagoItems.Failed ignored -> new BusStopAccess.Unavailable();
        };
    }

    private static BusStopAccess toAccess(List<JsonNode> nodes) {
        List<BusStop> stops = nodes.stream()
                .map(BusStopClientImpl::toStop)
                .flatMap(Optional::stream)
                .toList();
        // 항목은 있는데 전부 파싱 실패면 "주변에 없음" 이 아니라 스키마 변경·결측 신호다. 잘못된 "없음" 안내를 막는다.
        return stops.isEmpty() ? new BusStopAccess.Unavailable() : new BusStopAccess.Available(stops);
    }

    private static Optional<BusStop> toStop(JsonNode node) {
        String nodeId = node.path("nodeid").asText(null);
        String name = node.path("nodenm").asText(null);
        JsonNode lat = node.path("gpslati");
        JsonNode lng = node.path("gpslong");
        if (nodeId == null || nodeId.isBlank() || name == null || !lat.isNumber() || !lng.isNumber()) {
            return Optional.empty(); // 부분 결측 방어 — 이 항목만 건너뛴다
        }
        return Optional.of(new BusStop(nodeId, name, node.path("citycode").asInt(), lat.asDouble(), lng.asDouble()));
    }
}
