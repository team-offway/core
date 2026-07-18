package com.offway.core.external.probe;

import com.offway.core.external.ExternalApiProperties;
import java.net.URI;
import java.time.Duration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * data.go.kr(공공데이터포털) 계열 GET 프로브 공통.
 * serviceKey 없으면 SKIPPED, 예외는 잡아 FAIL로 — 절대 위로 던지지 않는다(페이지 렌더 보장).
 */
abstract class AbstractDataGoKrProbe implements ExternalApiProbe {

    protected static final String PROVIDER = "공공데이터포털";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    protected final WebClient webClient;
    protected final ExternalApiProperties props;

    protected AbstractDataGoKrProbe(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    protected abstract String name();

    /** serviceKey를 넣어 (이미 인코딩된) 최종 호출 URI를 만든다. */
    protected abstract URI uri(String serviceKey);

    @Override
    public ProbeResult probe() {
        if (!props.dataGoKr().hasKey()) {
            return ProbeResult.skipped(name(), PROVIDER);
        }
        try {
            String body = webClient.get()
                    .uri(uri(props.dataGoKr().serviceKey()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            String sample = ProbeSupport.snippet(body);
            if (isSuccess(body)) {
                return ProbeResult.ok(name(), PROVIDER, 200, sample);
            }
            return ProbeResult.fail(name(), PROVIDER, 200, "정상 코드(resultCode 00) 없음 — 키/승인/파라미터 확인", sample);
        } catch (WebClientResponseException e) {
            return ProbeResult.fail(name(), PROVIDER, e.getStatusCode().value(),
                    e.getMessage(), ProbeSupport.snippet(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return ProbeResult.fail(name(), PROVIDER, 0,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), "");
        }
    }

    /**
     * 정상 응답 판별. data.go.kr은 서비스마다 성공 코드 형식이 다르다
     * (특일정보=00, TourAPI=0000). resultMsg(OK/NORMAL SERVICE)도 함께 본다.
     */
    protected boolean isSuccess(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("\"resultCode\":\"00\"")
                || body.contains("\"resultCode\":\"0000\"")
                || body.contains("<resultCode>00</resultCode>")
                || body.contains("<resultCode>0000</resultCode>")
                || body.contains("NORMAL SERVICE")
                || body.contains("\"resultMsg\":\"OK\"");
    }
}
