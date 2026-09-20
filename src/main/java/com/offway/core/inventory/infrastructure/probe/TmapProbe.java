package com.offway.core.inventory.infrastructure.probe;

import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalHealthFilter;
import com.offway.core.common.logging.ExternalSystems;
import com.offway.core.common.logging.RootCause;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
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
    private final ExternalApiCallRecorder callRecorder;

    TmapProbe(WebClient externalWebClient, ExternalApiProperties props, ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    /**
     * 경로 탐색이다 — 경유지최적화(한도 50)가 아니라 {@link ExternalApi#TMAP_ROUTE}(1,000) 를 깎는다.
     *
     * <p>둘을 섞으면 안 된다. 경유지최적화는 하루 50 이라 프로브 48 콜이 들어가면 <b>그날 코스 동선을
     * 거의 못 짠다.</b> 같은 TMAP 이라고 한 항목으로 묶지 않는 이유다.
     */
    @Override
    public Optional<ExternalApi> quota() {
        return Optional.of(ExternalApi.TMAP_ROUTE);
    }

    @Override
    public ProbeResult probe() {
        if (!props.tmap().hasKey()) {
            return ProbeResult.skipped(NAME, PROVIDER);
        }
        // 실호출 직전에 센다(#594) — 응답이 실패해도 한도는 이미 깎였다(#123).
        quota().ifPresent(callRecorder::record);
        try {
            String body = webClient.post()
                    .uri(URL)
                    // 필터는 비켜선다 — 응답 본문까지 보고 스케줄러가 단독으로 적는다(#479).
                    .attribute(ExternalHealthFilter.SKIP_ATTRIBUTE, true)
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
                    RootCause.of(e), ProbeSupport.snippet(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return ProbeResult.fail(NAME, PROVIDER, 0,
                    RootCause.of(e), "");
        }
    }

    @Override
    public String system() {
        return ExternalSystems.label(URI.create(URL));
    }
}
