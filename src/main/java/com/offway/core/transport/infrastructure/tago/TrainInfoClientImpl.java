package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.infrastructure.tago.dto.TrainLeg;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TAGO 열차정보 adapter — {@code TrainInfoInqireService/getStrtpntAlocFndTrainInfo}(출발역·도착역·운행일자로 열차 목록).
 * data.go.kr 서비스라 같은 serviceKey 를 쓴다. 응답의 열차편 중 소요시간이 가장 짧은 것을 고른다.
 *
 * <p>키 없음·실패는 빈 Optional 폴백. data.go.kr 특유의 응답(resultCode·item 단일/배열·빈 items)을 방어한다.
 */
@Slf4j
@Component
class TrainInfoClientImpl implements TrainInfoClient {

    private static final String URL =
            "https://apis.data.go.kr/1613000/TrainInfoInqireService/getStrtpntAlocFndTrainInfo";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int ROWS = 100;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter PLAN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TrainInfoClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public Optional<TrainLeg> fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("_type", "json")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("depPlaceId", depStationId)
                .queryParam("arrPlaceId", arrStationId)
                .queryParam("depPlandTime", date.format(DATE));
        try {
            return parse(call(builder));
        } catch (Exception e) {
            log.warn("TAGO 열차정보 조회 실패 — 생략 cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 hex 라 재인코딩해도 안전.
        URI uri = builder.encode().build().toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private Optional<TrainLeg> parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            return Optional.empty();
        }
        JsonNode itemsNode = response.path("body").path("items");
        JsonNode items = itemsNode.isArray() ? itemsNode : itemsNode.path("item");
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty(); // 해당 날짜 미운행·빈 응답
        }
        return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(TrainInfoClientImpl::toLeg)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingInt(TrainLeg::durationMinutes));
    }

    private static Optional<TrainLeg> toLeg(JsonNode train) {
        LocalDateTime depart = toTime(train.path("depplandtime").asText());
        LocalDateTime arrive = toTime(train.path("arrplandtime").asText());
        if (depart == null || arrive == null || arrive.isBefore(depart)) {
            return Optional.empty(); // 시각 파싱 실패·역전은 건너뛴다(부분 결측 방어)
        }
        String type = train.path("traingradename").asText(null);
        return Optional.of(TrainLeg.of(type, depart, arrive));
    }

    private static LocalDateTime toTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), PLAN_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
