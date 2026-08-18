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
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
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
 * (resultCode·item 단일/배열·빈 items) 방어는 {@link TagoItems} 가 공통으로 처리한다.
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
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TrainInfoClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
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
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — 다른 data.go.kr 어댑터와 같은 규약.
        // 재인코딩하면 `%2B` 가 `%252B` 가 되어 서버가 다른 키로 읽는다(#165). 나머지 파라미터는 전부 ASCII 다.
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.TRAIN_INFO);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private TrainAvailability parse(String body) throws Exception {
        return switch (TagoItems.parse(body, objectMapper)) {
            case TagoItems.Items(List<JsonNode> nodes) -> legsOf(nodes);
            case TagoItems.Empty ignored -> new TrainAvailability.NoServiceOnDate(); // 조회 정상, 그 날짜 운행 없음
            case TagoItems.Failed ignored -> {
                // 예외가 아니라 정상 HTTP 응답이라 여기서 남기지 않으면 아무 흔적이 안 남는다. 제공기관 장애가
                // 조용히 "열차 없음" 으로 보이는 것을 막는다.
                log.warn("TAGO 열차정보 응답이 비정상 resultCode 입니다 — 조회 불가 처리");
                yield new TrainAvailability.Unavailable();
            }
        };
    }

    /**
     * 그 날짜에 운행하는 편 전부를 담는다 — <b>여기서 한 편으로 좁히지 않는다</b>(#138).
     *
     * <p>탈 수 있는 편은 출발 시각(연차·반차·반반차)에 따라 달라지는데, 어댑터는 그 시각을 모른다. 하루치를
     * 그대로 올려 보내면 캐시 한 엔트리가 세 단위를 모두 답한다 — 외부 호출이 단위 수만큼 늘지 않는다.
     */
    private static TrainAvailability legsOf(List<JsonNode> trains) {
        List<TrainLeg> legs = trains.stream()
                .map(TrainInfoClientImpl::toLeg)
                .flatMap(Optional::stream)
                .toList();
        if (legs.isEmpty()) {
            // items 에 편이 있는데 전부 파싱 실패면 미운행이 아니라 스키마 변경·결측 신호
            // → Unavailable(잘못된 "없음" 안내 방지).
            log.warn("TAGO 열차 {}편이 전부 파싱 실패했습니다 — 조회 불가 처리(스키마 변경 의심)", trains.size());
            return new TrainAvailability.Unavailable();
        }
        return new TrainAvailability.Available(legs);
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
