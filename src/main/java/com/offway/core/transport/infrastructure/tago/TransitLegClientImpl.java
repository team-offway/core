package com.offway.core.transport.infrastructure.tago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.transport.domain.MeasuredLeg;
import com.offway.core.transport.domain.TransitLegResult;
import com.offway.core.transport.domain.TransitMode;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TAGO 버스·여객선 구간 조회 adapter(#107 · #97). 수단별 경로·파라미터는 {@link TransitLegEndpoint} 가 든다.
 *
 * <p><b>시각 자릿수가 서비스마다 다르다.</b> 실측(2026-08-31) 결과 시외버스는 {@code 20260831070000}(14자리),
 * 고속버스·여객선은 {@code 202608310950}(12자리)다. 같은 계열이라고 한 포맷으로 파싱하면 한쪽이 통째로
 * 깨진다 — 그래서 <b>앞 12자리만</b> 읽는다. 초 단위는 어차피 소요시간 계산에 쓰지 않는다.
 *
 * <p>"운행 없음" 과 "조회 실패" 를 {@link TransitLegResult} 로 <b>갈라서</b> 준다. 결과가 DB 에 영구 기록되기
 * 때문이다 — 뭉개면 키가 없거나 한도가 마른 날의 실패가 "이 구간은 원래 안 다님" 으로 굳어, 멀쩡한 구간을
 * 배치가 다시는 재지 않는다.
 */
@Slf4j
@Component
class TransitLegClientImpl implements TransitLegClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final int ROWS = 50;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter PLAN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 응답 시각 문자열에서 실제로 읽는 길이 — 12자리(분)까지. 14자리로 오는 서비스는 초를 버린다. */
    private static final int PLAN_TIME_LENGTH = 12;

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TransitLegClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public TransitLegResult measure(TransitMode mode, String depCode, String arrCode, LocalDate date) {
        if (!props.dataGoKr().hasKey()) {
            // 키가 없는 것은 "운행 없음" 이 아니다. 여기서 NoService 를 주면 로컬·테스트 한 번으로
            // 전 구간이 미운행으로 기록된다(로컬 실행성 규칙상 키 없이도 부팅한다).
            return new TransitLegResult.Unavailable();
        }
        TransitLegEndpoint endpoint = TransitLegEndpoint.of(mode);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoint.url())
                .queryParam(TagoQuery.SERVICE_KEY, props.dataGoKr().serviceKey())
                .queryParam(TagoQuery.RESPONSE_TYPE, TagoQuery.RESPONSE_TYPE_JSON)
                .queryParam(TagoQuery.NUM_OF_ROWS, ROWS)
                .queryParam(TagoQuery.PAGE_NO, TagoQuery.FIRST_PAGE)
                .queryParam(endpoint.depKey(), depCode)
                .queryParam(endpoint.arrKey(), arrCode)
                .queryParam("depPlandTime", date.format(DATE));
        try {
            return parse(call(builder, endpoint), endpoint, mode, depCode, arrCode);
        } catch (Exception e) {
            log.warn("{} 구간 조회 실패 — 기록하지 않고 다음 배치에서 다시 잰다 {}→{} cause={}",
                    mode.label(), depCode, arrCode, RootCause.of(e));
            return new TransitLegResult.Unavailable();
        }
    }

    private String call(UriComponentsBuilder builder, TransitLegEndpoint endpoint) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — 재인코딩하면 `%2B` 가
        // `%252B` 가 되어 서버가 다른 키로 읽는다(#165).
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(endpoint.api());
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private TransitLegResult parse(
            String body, TransitLegEndpoint endpoint, TransitMode mode, String depCode, String arrCode)
            throws Exception {
        return switch (TagoItems.parse(body, objectMapper)) {
            case TagoItems.Items(List<JsonNode> nodes) -> shortest(nodes, endpoint, mode, depCode, arrCode);
            case TagoItems.Empty ignored -> {
                // 정상 응답인데 편이 없다 — 그 구간은 다니지 않는다. 결과이므로 warn 이 아니다.
                log.debug("{} 미운행 구간 {}→{}", mode.label(), depCode, arrCode);
                yield new TransitLegResult.NoService();
            }
            case TagoItems.Failed ignored -> {
                // 예외가 아니라 정상 HTTP 응답이라 여기서 안 남기면 흔적이 없다.
                log.warn("{} 구간 조회 응답이 비정상 resultCode 입니다 {}→{}", mode.label(), depCode, arrCode);
                yield new TransitLegResult.Unavailable();
            }
        };
    }

    /**
     * 편이 여럿이면 가장 짧은 것. 실측에서는 같은 구간의 편들이 전부 같은 소요시간이었지만, 그것이 계약은
     * 아니므로 최솟값으로 좁힌다 — 편차가 생기면 "가장 빠른 편" 이 우리가 답하고 싶은 값이다.
     */
    private static TransitLegResult shortest(
            List<JsonNode> nodes, TransitLegEndpoint endpoint, TransitMode mode, String depCode, String arrCode) {
        List<MeasuredLeg> legs = nodes.stream()
                .map(node -> toLeg(node, endpoint))
                .flatMap(Optional::stream)
                .toList();
        if (legs.isEmpty()) {
            // items 에 편이 있는데 전부 파싱 실패면 미운행이 아니라 스키마 변경·결측 신호다.
            // 미운행으로 적으면 스키마가 바뀐 날 전 구간이 통째로 굳는다.
            log.warn("{} {}편이 전부 파싱 실패했습니다 {}→{} — 스키마 변경 의심",
                    mode.label(), nodes.size(), depCode, arrCode);
            return new TransitLegResult.Unavailable();
        }
        return legs.stream()
                .min(Comparator.comparingInt(MeasuredLeg::minutes))
                .map(leg -> (TransitLegResult) new TransitLegResult.Measured(leg))
                .orElseGet(TransitLegResult.Unavailable::new);
    }

    private static Optional<MeasuredLeg> toLeg(JsonNode node, TransitLegEndpoint endpoint) {
        LocalDateTime depart = toTime(node.path("depPlandTime").asText());
        LocalDateTime arrive = toTime(node.path("arrPlandTime").asText());
        if (depart == null || arrive == null || !arrive.isAfter(depart)) {
            return Optional.empty(); // 시각 파싱 실패·역전·0분은 건너뛴다(부분 결측 방어)
        }
        int minutes = (int) Duration.between(depart, arrive).toMinutes();
        if (minutes <= 0) {
            return Optional.empty();
        }
        JsonNode charge = node.path("charge");
        return Optional.of(new MeasuredLeg(
                minutes,
                charge.isNumber() ? charge.asInt() : null,
                node.path(endpoint.vehicleField()).asText(null)));
    }

    /** 12자리·14자리를 함께 받는다 — 앞 12자리(분)까지만 읽고 초는 버린다. */
    private static LocalDateTime toTime(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() < PLAN_TIME_LENGTH) {
            return null;
        }
        try {
            return LocalDateTime.parse(trimmed.substring(0, PLAN_TIME_LENGTH), PLAN_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
