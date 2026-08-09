package com.offway.core.weather.infrastructure.airkorea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.AirGrade;
import com.offway.core.weather.domain.AirQuality;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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
     * 호출 상한 — 실측 분포에서 정했다(2026-08-10, 시도 17곳 각 1회).
     *
     * <pre>
     *   성공 13곳  0.082 0.096 0.110 0.121 0.121 0.139 0.145 0.181 0.181 0.186 0.199 0.225 0.283초
     *   실패  4곳  5.03 · 5.10 · 10.25 · 10.35초 → SERVICETIMEOUT_ERROR (에어코리아 게이트웨이가 스스로 끊는 값)
     * </pre>
     *
     * <p><b>백분위로 정하지 않았다.</b> 표본이 17건뿐이라 p99 를 주장할 수 없다. 대신 분포가 <b>두 무리로
     * 완전히 갈린다</b> — 성공은 0.28초 안에 다 오고, 그 밖은 5초·10초짜리 게이트웨이 타임아웃이다. 그 사이가
     * 통째로 비어 있으므로, 상한을 그 골짜기 안에 두면 어디에 두든 성공은 다 잡고 실패는 다 끊는다. 가장 느린
     * 성공(0.283초)의 다섯 배로 잡아 네트워크가 나쁜 날의 여유까지 뒀다.
     *
     * <p>예전 6초는 성공을 잡는 값이 아니라 <b>죽은 시도를 6초 기다리는</b> 값이었다. 홈이 시도 넷을 순차로
     * 물어 24초 걸린 요청이 실제로 있었다(그래서 대기질을 홈에서 코스로 옮겼다).
     */
    private static final Duration TIMEOUT = Duration.ofMillis(1500);
    private static final int ROWS = 100;

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AirKoreaClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
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
            return parse(call(builder));
        } catch (Exception e) {
            // 호출 하나하나는 debug 다. 워밍이 시도 17곳을 순차로 도는데 에어코리아가 통째로 죽으면
            // 한 번 돌 때마다 같은 warn 이 17줄 쌓여, 정작 봐야 할 사용자 요청 로그를 밀어낸다.
            // degrade 신호는 HomeCacheWarmer 가 실패 건수를 한 줄로 묶어 warn 으로 올린다.
            log.debug("에어코리아 대기질 조회 실패 — 생략 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // 인코딩이 필요한 값(한글 시도명)은 이미 넣을 때 인코딩했다. 여기서 통째로 다시 인코딩하면 serviceKey 의
        // `%2B` 가 `%252B` 가 되어 서버가 다른 키로 읽는다(#165).
        URI uri = builder.build(true).toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private Optional<AirQuality> parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            return Optional.empty();
        }
        JsonNode itemsNode = response.path("body").path("items");
        JsonNode items = itemsNode.isArray() ? itemsNode : itemsNode.path("item");
        if (!items.isArray() || items.isEmpty()) {
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
