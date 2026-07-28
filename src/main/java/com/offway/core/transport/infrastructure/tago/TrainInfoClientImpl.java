package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.transport.domain.TrainAvailability;
import com.offway.core.transport.domain.TrainLeg;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TAGO 열차정보 adapter — {@code TrainInfo/GetStrtpntAlocFndTrainInfo}(출발역·도착역·운행일자로 열차 목록).
 * data.go.kr 서비스라 같은 serviceKey 를 쓴다. 응답의 열차편 중 소요시간이 가장 짧은 것을 고른다.
 *
 * <p>경로 주의: 열차 서비스는 버스({@code ...InqireService}·소문자 op)와 달리 base 가 {@code TrainInfo}, 오퍼레이션이
 * <b>대문자 G</b>({@code GetStrtpntAlocFndTrainInfo})다 — data.go.kr 이 서비스마다 casing 이 다르다.
 *
 * <p>결과를 세 상태로 구분한다: 키 없음·호출/파싱 실패·비정상 resultCode 는 {@code Unavailable}(조용히 폴백), 정상 응답인데
 * 그 날짜 편이 없으면 {@code NoServiceOnDate}(사용자 안내 가능), 있으면 {@code Available}. data.go.kr 특유의 응답
 * (resultCode·item 단일/배열·빈 items)을 방어한다.
 */
@Slf4j
@Component
class TrainInfoClientImpl implements TrainInfoClient {

    private static final String URL =
            "https://apis.data.go.kr/1613000/TrainInfo/GetStrtpntAlocFndTrainInfo";
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
    public TrainAvailability fastestTrain(String depStationId, String arrStationId, LocalDate date) {
        if (!props.dataGoKr().hasKey()) {
            return new TrainAvailability.Unavailable();
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
            log.warn("TAGO 열차정보 조회 실패 — 조회 불가 처리 cause={}", e.getClass().getSimpleName());
            return new TrainAvailability.Unavailable();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 hex 라 재인코딩해도 안전.
        URI uri = builder.encode().build().toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private TrainAvailability parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            return new TrainAvailability.Unavailable(); // 외부가 정상 응답을 안 줬으니 실패로 본다
        }
        // 미운행이면 items 가 빈 문자열("")로 와서 item 은 missing. 결과가 하나면 item 이 배열이 아니라 객체 하나로 온다.
        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return new TrainAvailability.NoServiceOnDate(); // 조회 정상, 그 날짜 운행 없음
        }
        Stream<JsonNode> trains =
                item.isArray() ? StreamSupport.stream(item.spliterator(), false) : Stream.of(item);
        return trains.map(TrainInfoClientImpl::toLeg)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparingInt(TrainLeg::durationMinutes))
                .<TrainAvailability>map(TrainAvailability.Available::new)
                // items 에 편이 있는데 전부 파싱 실패면 미운행이 아니라 스키마 변경·결측 신호 → Unavailable(잘못된 "없음" 안내 방지).
                .orElseGet(TrainAvailability.Unavailable::new);
    }

    private static Optional<TrainLeg> toLeg(JsonNode train) {
        LocalDateTime depart = toTime(train.path("depplandtime").asText());
        LocalDateTime arrive = toTime(train.path("arrplandtime").asText());
        if (depart == null || arrive == null || !arrive.isAfter(depart)) {
            return Optional.empty(); // 시각 파싱 실패·역전·0분은 건너뛴다(부분 결측 방어)
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
