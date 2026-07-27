package com.offway.core.transport.infrastructure.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.Coordinate;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int SECONDS_PER_MINUTE = 60;
    private static final double METERS_PER_KM = 1000.0;

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TmapClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public Optional<TmapRoute> carRoute(Coordinate origin, Coordinate destination) {
        if (!props.tmap().hasKey()) {
            return Optional.empty();
        }
        try {
            String body = requestBody(origin, destination);
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
            // 키·URL 은 로그에 남기지 않는다. 실패는 폴백으로 흡수.
            log.warn("TMAP 경로 조회 실패 — 직선거리로 폴백 cause={}", e.getClass().getSimpleName());
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
}
