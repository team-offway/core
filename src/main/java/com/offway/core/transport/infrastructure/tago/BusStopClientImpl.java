package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.BusCity;
import com.offway.core.transport.domain.BusCoverage;
import com.offway.core.transport.domain.BusStop;
import com.offway.core.transport.domain.BusStopAccess;
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

    /** 커버 지자체 목록 — 같은 서비스의 다른 오퍼레이션. */
    private static final String CITY_CODE_URL =
            "https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCtyCodeList";

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /** 접근성 판정에는 가장 가까운 몇 곳이면 충분하다 — 전량을 받아 파싱할 이유가 없다. */
    private static final int ROWS = 20;

    /**
     * 도시목록은 <b>전량을 한 번에</b> 받는다. 실측 138곳인데 기본 페이지 크기로 요청하면 나머지가 통째로 미커버로 오판된다.
     * 지자체가 늘어날 여지를 두고 여유를 얹었다.
     */
    private static final int CITY_ROWS = 300;

    private static final String GPS_LATI = "gpsLati";
    private static final String GPS_LONG = "gpsLong";

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    BusStopClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public BusStopAccess nearbyStops(double lat, double lng) {
        if (!props.dataGoKr().hasKey()) {
            return new BusStopAccess.Unavailable();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam(TagoQuery.SERVICE_KEY, props.dataGoKr().serviceKey())
                .queryParam(TagoQuery.RESPONSE_TYPE, TagoQuery.RESPONSE_TYPE_JSON)
                .queryParam(TagoQuery.NUM_OF_ROWS, ROWS)
                .queryParam(TagoQuery.PAGE_NO, TagoQuery.FIRST_PAGE)
                .queryParam(GPS_LATI, lat)
                .queryParam(GPS_LONG, lng);
        try {
            return parse(call(builder));
        } catch (Exception e) {
            log.warn("TAGO 버스정류소 조회 실패 — 조회 불가 처리 cause={}", e.getClass().getSimpleName());
            return new BusStopAccess.Unavailable();
        }
    }

    @Override
    public Optional<BusCoverage> coveredCities() {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(CITY_CODE_URL)
                .queryParam(TagoQuery.SERVICE_KEY, props.dataGoKr().serviceKey())
                .queryParam(TagoQuery.RESPONSE_TYPE, TagoQuery.RESPONSE_TYPE_JSON)
                .queryParam(TagoQuery.NUM_OF_ROWS, CITY_ROWS)
                .queryParam(TagoQuery.PAGE_NO, TagoQuery.FIRST_PAGE);
        try {
            return parseCities(call(builder));
        } catch (Exception e) {
            log.warn("TAGO 도시목록 조회 실패 — 커버 판별 불가 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<BusCoverage> parseCities(String body) throws Exception {
        return switch (TagoItems.parse(body, objectMapper)) {
            case TagoItems.Items(List<JsonNode> nodes) -> toCoverage(nodes);
            case TagoItems.Empty ignored -> {
                // 빈 목록을 그대로 믿으면 전국이 미커버가 된다. 조용히 넘기지 않고 실패로 본다.
                log.warn("TAGO 도시목록이 비어 왔습니다 — 커버 판별 불가");
                yield Optional.empty();
            }
            case TagoItems.Failed ignored -> {
                log.warn("TAGO 도시목록 응답이 비정상 resultCode 입니다 — 커버 판별 불가");
                yield Optional.empty();
            }
        };
    }

    private static Optional<BusCoverage> toCoverage(List<JsonNode> nodes) {
        List<BusCity> cities =
                nodes.stream().map(BusStopClientImpl::toCity).flatMap(Optional::stream).toList();
        if (cities.isEmpty()) {
            log.warn("TAGO 도시목록 {}건이 전부 파싱 실패했습니다 — 커버 판별 불가", nodes.size());
            return Optional.empty();
        }
        return Optional.of(new BusCoverage(cities));
    }

    private static Optional<BusCity> toCity(JsonNode node) {
        JsonNode code = node.path("citycode");
        String name = node.path("cityname").asText(null);
        // 부분 결측 방어 — 이 항목만 건너뛴다. 코드가 없으면 어느 시도인지 못 갈라내 판정에 쓸 수 없다.
        if (!code.isIntegralNumber() || code.asInt() <= 0 || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BusCity(code.asInt(), name));
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — TourApiClientImpl 과 동일 규약.
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.BUS_STOP);
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
        JsonNode cityCode = node.path("citycode");
        // 부분 결측 방어 — 이 항목만 건너뛴다. citycode 는 도착정보 조회에 필수라 정수 아님·0 이하면 제외한다
        // (asInt() 는 결측·비정수도 0 을 돌려줘 방어를 우회하므로 isIntegralNumber 로 확인).
        if (nodeId == null || nodeId.isBlank() || name == null || name.isBlank()
                || !lat.isNumber() || !lng.isNumber()
                || !cityCode.isIntegralNumber() || cityCode.asInt() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new BusStop(nodeId, name, cityCode.asInt(), lat.asDouble(), lng.asDouble()));
    }
}
