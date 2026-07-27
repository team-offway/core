package com.offway.core.weather.infrastructure.kma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.Grid;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.infrastructure.kma.dto.DailyWeather;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기상청 단기예보(동네예보) adapter — {@code VilageFcstInfoService/getVilageFcst}. data.go.kr 서비스라 TourAPI 와 같은
 * serviceKey 를 쓴다. 위경도를 격자({@link Grid})로 바꿔 조회하고, 카테고리별 예보(TMN·TMX·SKY·POP)를 하루 요약으로 집계한다.
 *
 * <p>키 없음·실패·예보 범위 밖은 빈 Optional 로 폴백(날씨는 부가 정보). 발표시각(02·05·08·11·14·17·20·23시)의 최신본을 쓴다.
 */
@Slf4j
@Component
class KmaWeatherClientImpl implements KmaWeatherClient {

    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService/getVilageFcst";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int ROWS = 300; // 하루치 카테고리 예보를 넉넉히
    private static final int[] BASE_HOURS = {23, 20, 17, 14, 11, 8, 5, 2};
    private static final int PUBLISH_DELAY_MIN = 10; // 발표 후 제공까지 지연
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    KmaWeatherClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public Optional<DailyWeather> dailyForecast(double lat, double lng, LocalDate date) {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        Grid grid = Grid.from(lat, lng);
        String[] base = baseDateTime(LocalDateTime.now());
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("base_date", base[0])
                .queryParam("base_time", base[1])
                .queryParam("nx", grid.nx())
                .queryParam("ny", grid.ny());
        try {
            return parse(call(builder), date);
        } catch (Exception e) {
            log.warn("기상청 단기예보 조회 실패 — 날씨 생략 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String call(UriComponentsBuilder builder) {
        URI uri = builder.build(true).toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    /** 발표시각 중 (현재-10분) 이하의 최신 슬롯. 02시 이전이면 전날 23시. */
    private String[] baseDateTime(LocalDateTime now) {
        LocalDateTime t = now.minusMinutes(PUBLISH_DELAY_MIN);
        for (int hour : BASE_HOURS) {
            if (t.getHour() >= hour) {
                return new String[] {t.toLocalDate().format(YMD), String.format("%02d00", hour)};
            }
        }
        return new String[] {t.toLocalDate().minusDays(1).format(YMD), "2300"};
    }

    private Optional<DailyWeather> parse(String body, LocalDate date) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            return Optional.empty();
        }
        String target = date.format(YMD);
        Integer minTemp = null;
        Integer maxTemp = null;
        SkyState sky = SkyState.UNKNOWN;
        Integer rainProb = null;
        boolean any = false;

        for (JsonNode item : response.path("body").path("items").path("item")) {
            if (!target.equals(item.path("fcstDate").asText())) {
                continue;
            }
            any = true;
            String category = item.path("category").asText();
            String value = item.path("fcstValue").asText();
            switch (category) {
                case "TMN" -> minTemp = toInt(value);
                case "TMX" -> maxTemp = toInt(value);
                case "POP" -> rainProb = maxOf(rainProb, toInt(value));
                case "SKY" -> {
                    if (sky == SkyState.UNKNOWN || "1200".equals(item.path("fcstTime").asText())) {
                        sky = SkyState.fromCode(value); // 정오값을 대표로
                    }
                }
                default -> { /* 그 외 카테고리는 사용하지 않음 */ }
            }
        }
        return any ? Optional.of(new DailyWeather(date, minTemp, maxTemp, sky, rainProb)) : Optional.empty();
    }

    private static Integer toInt(String value) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.max(current, candidate);
    }
}
