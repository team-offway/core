package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** TMAP 자동차 경로(SK) — 서울시청→강남역 샘플로 appKey·연결 확인. */
@Component
class TmapProbe implements ExternalApiProbe {

    private static final String NAME = "TMAP 경로";
    private static final String PROVIDER = "SK openapi";
    private static final String URL = "https://apis.openapi.sk.com/tmap/routes?version=1";
    private static final String SAMPLE_BODY =
            "{\"startX\":\"126.9779\",\"startY\":\"37.5663\",\"endX\":\"127.0276\",\"endY\":\"37.4979\","
            + "\"reqCoordType\":\"WGS84GEO\",\"resCoordType\":\"WGS84GEO\",\"searchOption\":\"0\"}";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final WebClient webClient;
    private final ExternalApiProperties props;

    TmapProbe(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public ProbeResult probe() {
        if (!props.tmap().hasKey()) {
            return ProbeResult.skipped(NAME, PROVIDER);
        }
        try {
            String body = webClient.post()
                    .uri(URL)
                    .header("appKey", props.tmap().appKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(SAMPLE_BODY)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            String sample = ProbeSupport.snippet(body);
            if (body != null && body.contains("totalTime")) {
                return ProbeResult.ok(NAME, PROVIDER, 200, sample);
            }
            return ProbeResult.fail(NAME, PROVIDER, 200, "totalTime 없음", sample);
        } catch (WebClientResponseException e) {
            return ProbeResult.fail(NAME, PROVIDER, e.getStatusCode().value(),
                    e.getMessage(), ProbeSupport.snippet(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return ProbeResult.fail(NAME, PROVIDER, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), "");
        }
    }
}
