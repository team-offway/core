package com.offway.core.trip.infrastructure.datalab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.domain.VisitorType;
import com.offway.core.trip.infrastructure.datalab.dto.RegionVisitor;
import com.offway.core.trip.infrastructure.datalab.dto.TourVisitorResult;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 관광빅데이터(DataLabService) adapter — 기초 시군구별 일별 방문자수(locgoRegnVisitrDDList).
 *
 * <p>키가 없으면 외부 호출 없이 빈 결과(로컬 실행성). 호출·파싱 실패는 {@link TourApiException}(502)으로 올린다. data.go.kr
 * 함정(비성공코드인데 items 없음, 1건 단일객체, 결과없음 items 빈문자열)을 방어한다. 알 수 없는 방문자 구분(touDivCd)은 건너뛴다.
 */
@Slf4j
@Component
class TourDataLabClientImpl implements TourDataLabClient {

    private static final String BASE =
            "https://apis.data.go.kr/B551011/DataLabService/locgoRegnVisitrDDList";
    /**
     * 다른 외부 클라이언트(6초)보다 길게 잡는다. 이 오퍼레이션은 <b>지역 필터 파라미터가 없어</b> 기간 전체가 한 번에
     * 내려온다 — 관측 창 7일이 268 시군구 × 3 구분 × 7일 = 5,628건, 약 <b>1MB</b> 다. 실측 응답시간이 0.3초~8.8초로
     * 출렁여(같은 요청 3회: 0.5s · 8.8s · 0.3s) 6초로는 간헐 실패한다.
     *
     * <p>길게 둬도 사용자 지연으로 새지 않는다 — 결과는 6시간 캐시되고 워머가 미리 채우므로 이 대기를 감당하는 건
     * 대부분 백그라운드다. 반대로 짧게 두면 랭킹 가중치가 통째로 날아가 순위가 무의미해진다(실패 시 폴백이 빈 가중치).
     *
     * <p>이 값은 한 건의 <b>기본</b> 상한이다. 호출자가 남은 예산({@code maxWait})을 더 짧게 주면 그쪽을 따른다 —
     * 집계 전체 상한을 가진 호출자가 마지막 한 건 때문에 그 상한을 넘기지 않게.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TourDataLabClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public TourVisitorResult findRegionVisitors(
            LocalDate from, LocalDate to, int pageNo, int numOfRows, Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            log.info("관광빅데이터 키 없음 — 방문자수 조회를 건너뜁니다");
            return TourVisitorResult.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("startYmd", from.format(YMD))
                .queryParam("endYmd", to.format(YMD))
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .build(true)
                .toUri();
        // 짧은 쪽을 따른다 — 호출자의 남은 예산을 넘겨 기다리면 호출자의 전체 상한이 무의미해진다.
        Duration wait = maxWait.compareTo(TIMEOUT) < 0 ? maxWait : TIMEOUT;
        try {
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parse(body);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("관광빅데이터 조회 실패 cause={}", e.getClass().getSimpleName());
            throw TourApiException.dataLabLookupFailed(e);
        }
    }

    private TourVisitorResult parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");

        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException("관광빅데이터 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }

        JsonNode bodyNode = response.path("body");
        int totalCount = bodyNode.path("totalCount").asInt(0);

        List<RegionVisitor> items = new ArrayList<>();
        JsonNode item = bodyNode.path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return new TourVisitorResult(items, totalCount);
        }
        for (JsonNode node : item.isArray() ? item : objectMapper.createArrayNode().add(item)) {
            toVisitor(node).ifPresent(items::add);
        }
        return new TourVisitorResult(items, totalCount);
    }

    /**
     * 한 건을 방문자 레코드로 변환한다. 이상 데이터(알 수 없는 구분, 필수값 누락, 날짜·숫자 형식 오류)는 <b>그 한 건만</b> 건너뛴다(빈
     * Optional). 한 건이 전체 요청을 502로 터뜨리거나(잘못된 날짜) 0명으로 집계를 오염시키지(잘못된 방문자수) 않게 국소화한다.
     */
    private Optional<RegionVisitor> toVisitor(JsonNode node) {
        Optional<VisitorType> type = VisitorType.fromCode(node.path("touDivCd").asText());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        String signguName = node.path("signguNm").asText();
        String baseYmd = node.path("baseYmd").asText();
        String touNum = node.path("touNum").asText();
        if (signguName.isBlank() || baseYmd.isBlank() || touNum.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RegionVisitor(
                    node.path("signguCode").asText(),
                    signguName,
                    LocalDate.parse(baseYmd, YMD),
                    type.get(),
                    Double.parseDouble(touNum)));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
