package com.offway.core.weather.infrastructure.airkorea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import reactor.util.retry.Retry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 에어코리아 대기오염정보 adapter — {@code ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty}(시도별 실시간). data.go.kr
 * 서비스라 같은 serviceKey 를 쓴다. 시도 안 측정소들의 미세먼지·초미세먼지를 평균 내고 통합등급은 가장 나쁜 값을 취한다.
 *
 * <p>키 없음·실패는 빈 Optional 폴백. 측정 결측치("-" 등)는 평균에서 제외한다.
 */
@Slf4j
@Component
class AirKoreaClientImpl implements AirKoreaClient {

    private static final String URL =
            "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty";
    /**
     * 호출 상한 — 이제 <b>워밍(배경) 예산</b>이다. 요청 경로는 캐시에 있는 값만 쓰므로 사용자가 이 시간을
     * 기다리지 않는다({@code AirQualityService#cached}).
     *
     * <p>실측(2026-08-10, 시도 17곳 × 6회 = 102건)이 이 API 의 성격을 보여준다.
     *
     * <pre>
     *   성공 65건  p50 0.157 · p90 2.74 · p95 5.42 · max 30.0초
     *   실패 37건  5.0~12.8초 → SERVICETIMEOUT_ERROR   (실패율 36%)
     * </pre>
     *
     * <p><b>처음엔 17건만 재고 1.5초로 줄이려 했다.</b> 그 표본에서는 성공이 전부 0.3초 안에 왔고 실패는
     * 5초·10초라 "분포가 두 무리로 갈린다" 고 봤는데, 표본을 여섯 배로 늘리자 성공이 30초까지 퍼졌다.
     * 1.5초로 잘랐다면 <b>성공 응답의 15%를 죽였을</b> 것이다(규약의 "분포 안쪽을 자르면 간헐 실패가 된다").
     *
     * <p>그래서 상한을 줄이는 대신 <b>요청 경로에서 이 호출을 뺐다.</b> 이만큼 흔들리는 API 는 어떤 값을
     * 골라도 사용자가 기다릴 값이 못 된다. 6초는 성공의 약 95%를 잡는 배경 예산으로 남긴다.
     *
     * <p>이 값은 <b>시도 하나</b>의 상한이다. 재시도까지 합친 상한은 {@link #TOTAL_DEADLINE} 이 따로 든다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int ROWS = 100;

    /**
     * 간헐 504 를 한 번만 다시 건다.
     *
     * <p><b>실패율은 시각에 따라 크게 흔들린다.</b> 같은 방식으로 두 번 쟀는데 결과가 이만큼 달랐다.
     *
     * <pre>
     *   2026-08-09  시도 17곳 × 3회 = 51건 →  504  5건 (10%)
     *   2026-08-10  시도 17곳 × 3회 = 51건 →  504 16건 (31%)   ← 심야
     * </pre>
     *
     * <p>둘 다 시도를 가리지 않고 흩어져 있었고 한 시도가 연달아 죽는 경우는 없었다 — 제공기관 게이트웨이
     * 문제다. 그래서 한 번만 다시 걸어도 둘 다 실패할 확률이 눈에 띄게 떨어진다(31% 기준으로도 약 10%).
     *
     * <p>여러 번 걸지 않는 이유: 캐시 TTL 이 1시간이라 실패가 굳으면 그동안 대기질이 비지만, 재시도를 늘리면
     * 죽어 있는 동안 배경 워밍이 그만큼 오래 붙들린다. 요청 경로는 이 지연을 물지 않는다 — 캐시에 있는
     * 값만 쓰기 때문이다({@code AirQualityService#cached}).
     */
    private static final int RETRY_ATTEMPTS = 1;

    /** 재시도 사이 간격 — 게이트웨이가 숨 돌릴 만큼만. */
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(300);

    /**
     * 작업 전체 상한 — 재시도·백오프를 <b>포함</b>한 값이다.
     *
     * <p>호출 하나의 timeout 과 작업 전체의 deadline 은 별개다(성능 규약). {@link #TIMEOUT} 만 두면 시도
     * 하나가 6초씩이라 재시도까지 12.3초가 되고, {@code AirController} 경로에서는 그 시간이 사용자 응답에
     * 그대로 붙는다.
     *
     * <p>{@code TIMEOUT × 2 + 백오프} 에 여유를 조금 얹은 값이다 — 정상적인 재시도 한 번은 끊지 않으면서,
     * 그보다 오래 끄는 경우는 여기서 자른다.
     */
    private static final Duration TOTAL_DEADLINE = Duration.ofMillis(13_000);

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AirKoreaClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public Optional<AirQuality> realtimeBySido(String airKoreaSidoName) {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("returnType", "json")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                // 시도명은 한글이라 여기서 직접 인코딩한다 — URI 를 통째로 재인코딩하면 serviceKey 까지 이중
                // 인코딩돼 다른 키가 된다(#165).
                .queryParam("sidoName", URLEncoder.encode(airKoreaSidoName, StandardCharsets.UTF_8))
                .queryParam("ver", "1.3");
        try {
            return parse(airKoreaSidoName, call(builder));
        } catch (Exception e) {
            // 호출 하나하나는 debug 다. 워밍이 시도 17곳을 순차로 도는데 에어코리아가 통째로 죽으면
            // 한 번 돌 때마다 같은 warn 이 17줄 쌓여, 정작 봐야 할 사용자 요청 로그를 밀어낸다.
            // degrade 신호는 HomeCacheWarmer 가 실패 건수를 한 줄로 묶어 warn 으로 올린다.
            //
            // 다만 <b>무엇이 실패했는지는 남긴다.</b> 예전에는 cause 가 늘 `ReactiveException` 이었다 —
            // WebClient 가 감싼 껍데기라 키 문제인지 timeout 인지 제공기관 장애인지 구분할 수 없었고,
            // 원인을 찾으려면 따로 실호출을 떠야 했다.
            log.debug("에어코리아 대기질 조회 실패 — 생략 sido={} cause={}",
                    airKoreaSidoName, RootCause.of(e));
            return Optional.empty();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // 인코딩이 필요한 값(한글 시도명)은 이미 넣을 때 인코딩했다. 여기서 통째로 다시 인코딩하면 serviceKey 의
        // `%2B` 가 `%252B` 가 되어 서버가 다른 키로 읽는다(#165).
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.AIR_KOREA);
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                // timeout 을 retry 안쪽에 둬 시도 하나에 걸리게 한다 — 바깥에 두면 재시도까지 합쳐 6초가 된다.
                .timeout(TIMEOUT)
                .retryWhen(Retry.fixedDelay(RETRY_ATTEMPTS, RETRY_BACKOFF).filter(AirKoreaClientImpl::isGatewayTimeout))
                // 재시도 바깥의 전체 상한 — 호출 하나의 상한과 별개다(성능 규약).
                .timeout(TOTAL_DEADLINE)
                .block();
    }

    /**
     * 다시 걸어볼 만한 실패인가 — <b>504 만</b>이다.
     *
     * <p>필터가 없으면 모든 예외를 재시도한다. 키 오류(401)·파라미터 오류(400)처럼 다시 걸어도 같은 답이
     * 오는 것까지 한 번 더 물어 지연만 두 배가 되고, {@link #TIMEOUT} 이 만든 {@code TimeoutException} 도
     * 재시도 대상이 된다 — 이미 너무 느려서 끊은 호출을 다시 걸어 <b>대기 시간을 두 배로 만드는</b> 셈이다.
     *
     * <p>재시도가 노린 것은 제공기관 게이트웨이의 간헐 504 하나다. 실측(2026-08-10, 51회)에서
     * {@code SERVICETIMEOUT_ERROR} 는 전부 <b>HTTP 504</b> 로 왔고(16건, 31%), 성공은 전부 200 이었다.
     */
    private static boolean isGatewayTimeout(Throwable error) {
        return error instanceof WebClientResponseException.GatewayTimeout;
    }

    private Optional<AirQuality> parse(String sido, String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        String resultCode = response.path("header").path("resultCode").asText();
        if (!"00".equals(resultCode)) {
            // 제공기관이 주는 사유를 그대로 남긴다 — 504 는 여기가 아니라 예외로 오지만,
            // 키 만료·파라미터 오류는 성공 HTTP 에 실패 코드로 온다.
            log.debug("에어코리아 실패 코드 sido={} resultCode={} msg={}",
                    sido, resultCode, response.path("header").path("resultMsg").asText());
            return Optional.empty();
        }
        JsonNode itemsNode = response.path("body").path("items");
        JsonNode items = itemsNode.isArray() ? itemsNode : itemsNode.path("item");
        if (!items.isArray() || items.isEmpty()) {
            // <b>성공 코드에 측정소 0건.</b> 504 와 결과는 같지만 원인이 다르다 — 실측에서 광주·전남이
            // 3회 모두 이랬다. 예외가 아니라 아무 흔적이 없어, 구분해 남기지 않으면 계속 비는 것을 모른다.
            log.warn("에어코리아 빈 응답(성공 코드에 측정소 0건) sido={}", sido);
            return Optional.empty();
        }

        long pm10Sum = 0;
        int pm10Count = 0;
        long pm25Sum = 0;
        int pm25Count = 0;
        AirGrade worst = AirGrade.UNKNOWN;
        for (JsonNode station : items) {
            Integer pm10 = toInt(station.path("pm10Value").asText());
            if (pm10 != null) {
                pm10Sum += pm10;
                pm10Count++;
            }
            Integer pm25 = toInt(station.path("pm25Value").asText());
            if (pm25 != null) {
                pm25Sum += pm25;
                pm25Count++;
            }
            worst = worst.worse(AirGrade.fromKhai(station.path("khaiGrade").asText()));
        }
        return Optional.of(new AirQuality(
                average(pm10Sum, pm10Count), average(pm25Sum, pm25Count), worst));
    }


    private static Integer average(long sum, int count) {
        return count == 0 ? null : Math.round((float) sum / count);
    }

    private static Integer toInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null; // "-" 등 결측치
        }
    }
}
