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
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
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
            log.warn("에어코리아 대기질 조회 실패 — 생략 cause={}", e.getClass().getSimpleName());
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
