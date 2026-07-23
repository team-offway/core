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
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
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
    public TourVisitorResult findRegionVisitors(LocalDate from, LocalDate to, int pageNo, int numOfRows) {
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
        try {
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
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

    /** 알 수 없는 방문자 구분(touDivCd)이나 파싱 불가한 값은 건너뛴다(빈 Optional). */
    private Optional<RegionVisitor> toVisitor(JsonNode node) {
        Optional<VisitorType> type = VisitorType.fromCode(node.path("touDivCd").asText());
        if (type.isEmpty()) {
            return Optional.empty();
        }
        String baseYmd = node.path("baseYmd").asText();
        if (baseYmd.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RegionVisitor(
                node.path("signguCode").asText(),
                node.path("signguNm").asText(),
                LocalDate.parse(baseYmd, YMD),
                type.get(),
                node.path("touNum").asDouble(0)));
    }
}
