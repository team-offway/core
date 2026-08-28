package com.offway.core.transport.infrastructure.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * TMAP 자동차 경로 adapter (SK 오픈API {@code /tmap/routes}). 인증은 {@code appKey} 헤더.
 *
 * <p>키가 없으면 외부 호출 없이 빈 결과(로컬 실행성). 호출·파싱 실패도 빈 결과로 돌려 상위(코스)가 직선거리로 폴백하게 한다 —
 * 이동시간은 보조 정보라 실패로 코스 전체를 막지 않는다. 좌표는 TMAP 규약대로 X=경도·Y=위도(WGS84GEO).
 */
@Slf4j
@Component
class TmapClientImpl implements TmapClient {

    private static final String ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes?version=1";
    private static final String OPTIMIZE_URL = "https://apis.openapi.sk.com/tmap/routes/routeOptimization10";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int SECONDS_PER_MINUTE = 60;
    private static final double METERS_PER_KM = 1000.0;
    /** routeOptimization10: 출발+경유(≥1)+도착 = 최소 3, 경유지 최대 10곳(총 12). */
    private static final int MIN_OPTIMIZE_POINTS = 3;
    private static final int MAX_OPTIMIZE_POINTS = 12;
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TmapClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public Optional<TmapRoute> carRoute(Coordinate origin, Coordinate destination) {
        if (!props.tmap().hasKey()) {
            return Optional.empty();
        }
        try {
            String body = requestBody(origin, destination);
            // 경로 탐색과 경유지 최적화는 한도가 다르다(1,000 vs 50). 같은 클라이언트지만 따로 센다.
            callRecorder.record(ExternalApi.TMAP_ROUTE);
            String response = webClient.post()
                    .uri(ROUTES_URL)
                    .header("appKey", props.tmap().appKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            return parse(response);
        } catch (Exception e) {
            // 실패는 폴백으로 흡수하되 **사유는 남긴다**. TMAP 은 거절 이유를 응답 본문의 code 로 주는데
            // (1100 도로 링크 없음 · 1009 한반도 범위 초과) 예외 클래스명은 둘 다 BadRequest 라 못 가른다.
            // 그 한 줄이 없어 원인을 찾는 데 실호출 210건이 들었다(#334). RootCause 가 키·URL 은 가린다.
            log.warn("TMAP 경로 조회 실패 — 직선거리로 폴백 cause={}", RootCause.of(e));
            return Optional.empty();
        }
    }

    private String requestBody(Coordinate origin, Coordinate destination) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "startX", origin.lng(), "startY", origin.lat(),
                "endX", destination.lng(), "endY", destination.lat(),
                "reqCoordType", "WGS84GEO", "resCoordType", "WGS84GEO",
                "searchOption", "0", "startName", "출발", "endName", "도착"));
    }

    /** TMAP GeoJSON 의 첫 feature properties 에 요약(totalTime 초·totalDistance m)이 온다. */
    private Optional<TmapRoute> parse(String body) throws Exception {
        JsonNode features = objectMapper.readTree(body).path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        JsonNode properties = features.get(0).path("properties");
        int totalTime = properties.path("totalTime").asInt(0);
        int totalDistance = properties.path("totalDistance").asInt(0);
        if (totalTime <= 0) {
            return Optional.empty();
        }
        int minutes = Math.max(1, Math.round((float) totalTime / SECONDS_PER_MINUTE));
        return Optional.of(new TmapRoute(minutes, totalDistance / METERS_PER_KM));
    }

    @Override
    public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
        if (!props.tmap().hasKey() || points.size() < MIN_OPTIMIZE_POINTS || points.size() > MAX_OPTIMIZE_POINTS) {
            return Optional.empty();
        }
        try {
            // 우리가 가진 것 중 가장 빡빡한 한도(50/일). #110 에서 80% 소진 알림을 실제로 받았다.
            callRecorder.record(ExternalApi.TMAP_WAYPOINT);
            String response = webClient.post()
                    .uri(OPTIMIZE_URL)
                    .header("appKey", props.tmap().appKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(optimizeBody(points))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            return parseOrder(response, points.size());
        } catch (Exception e) {
            log.warn("TMAP 경유지 최적화 실패 — 직선거리 정렬로 폴백 cause={}", RootCause.of(e));
            return Optional.empty();
        }
    }

    private String optimizeBody(List<Coordinate> points) throws Exception {
        Coordinate start = points.get(0);
        Coordinate end = points.get(points.size() - 1);
        List<Map<String, Object>> vias = new ArrayList<>();
        for (int i = 1; i < points.size() - 1; i++) {
            Coordinate via = points.get(i);
            vias.add(Map.of(
                    "viaPointId", String.valueOf(i), "viaPointName", "v" + i,
                    "viaX", String.valueOf(via.lng()), "viaY", String.valueOf(via.lat())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        body.put("carType", "0");
        body.put("startName", "출발");
        body.put("startX", String.valueOf(start.lng()));
        body.put("startY", String.valueOf(start.lat()));
        body.put("startTime", LocalDateTime.now().format(START_TIME));
        body.put("endName", "도착");
        body.put("endX", String.valueOf(end.lng()));
        body.put("endY", String.valueOf(end.lat()));
        body.put("viaPoints", vias);
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 응답의 Point feature 로 최적 순서를 복원한다. pointType 이 {@code S}(출발)·{@code B{n}}(경유 n번째)·{@code E}(도착)
     * 이고, 경유 지점은 {@code viaPointId}(우리가 심은 입력 인덱스)로 원 위치를 안다. 예상 개수와 다르면 폴백을 위해 빈 결과.
     */
    private Optional<List<Integer>> parseOrder(String body, int size) throws Exception {
        JsonNode features = objectMapper.readTree(body).path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        List<int[]> vias = new ArrayList<>(); // [순서n, 원본인덱스]
        for (JsonNode feature : features) {
            if (!"Point".equals(feature.path("geometry").path("type").asText())) {
                continue;
            }
            String pointType = feature.path("properties").path("pointType").asText("");
            if (pointType.startsWith("B")) {
                int sequence = Integer.parseInt(pointType.substring(1));
                int original = Integer.parseInt(feature.path("properties").path("viaPointId").asText());
                vias.add(new int[] {sequence, original});
            }
        }
        if (vias.size() != size - 2) {
            return Optional.empty();
        }
        vias.sort(Comparator.comparingInt(via -> via[0]));
        List<Integer> order = new ArrayList<>();
        order.add(0);
        vias.forEach(via -> order.add(via[1]));
        order.add(size - 1);
        return Optional.of(order);
    }
}
