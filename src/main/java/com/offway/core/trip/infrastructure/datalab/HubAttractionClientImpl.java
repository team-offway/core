package com.offway.core.trip.infrastructure.datalab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.dto.HubAttractionItem;
import java.net.URI;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기초지자체 중심 관광지 adapter — {@code LocgoHubTarService1/areaBasedList1}.
 *
 * <p><b>코드 체계가 TourAPI 와 다르다.</b> 데이터랩은 <b>법정동 코드</b>를 쓴다 — 공주시는 {@code areaCd=44},
 * {@code signguCd=44150} 이다. TourAPI 코드(34/1)를 넣으면 {@code resultCode=0000} 에 {@code totalCount=0} 이
 * 돌아온다. 성공 코드라 조용히 빈 결과가 되므로 특히 조심해야 한다.
 *
 * <p><b>{@code pageNo} 가 필수다.</b> 빠뜨려도 성공 코드에 빈 결과가 온다 — 실제로 조사 중 이걸로 "최신 월이 없다"
 * 고 잘못 읽었다. 필수 파라미터 누락을 오류로 알려주지 않는 API 라 호출부에서 지켜야 한다.
 *
 * <p>키가 없으면 외부 호출 없이 빈 목록(로컬 실행성). 호출·파싱 실패는 {@link TourApiException}(502)으로 올린다.
 */
@Slf4j
@Component
class HubAttractionClientImpl implements HubAttractionClient {

    private static final String URL = "https://apis.data.go.kr/B551011/LocgoHubTarService1/areaBasedList1";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");
    private static final DateTimeFormatter BASE_YM = DateTimeFormatter.ofPattern("yyyyMM");
    /** 법정동 시군구코드 5자리 중 앞 2자리가 시도코드다. */
    private static final int AREA_CODE_LENGTH = 2;

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    HubAttractionClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public List<HubAttractionItem> findByRegion(String legalCode, YearMonth baseMonth, int rows) {
        if (!props.dataGoKr().hasKey()) {
            log.info("데이터랩 키 없음 — 중심 관광지 조회를 건너뜁니다");
            return List.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("areaCd", legalCode.substring(0, AREA_CODE_LENGTH))
                .queryParam("signguCd", legalCode)
                .queryParam("baseYm", baseMonth.format(BASE_YM))
                .queryParam("numOfRows", rows)
                // 빠뜨리면 성공 코드에 빈 결과가 온다. 필수다.
                .queryParam("pageNo", 1);
        try {
            return parse(call(builder));
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("중심 관광지 조회 실패 legalCode={} cause={}", legalCode, e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(#165).
        URI uri = builder.build(true).toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private List<HubAttractionItem> parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!SUCCESS_CODES.contains(response.path("header").path("resultCode").asText())) {
            return List.of();
        }
        JsonNode itemsNode = response.path("body").path("items");
        // 결과가 없으면 items 가 빈 문자열로 온다(data.go.kr 함정).
        if (itemsNode.isTextual()) {
            return List.of();
        }
        JsonNode items = itemsNode.path("item");
        List<HubAttractionItem> parsed = new ArrayList<>();
        // 1건이면 item 이 배열이 아니라 단일 객체다(또 다른 함정).
        if (items.isObject()) {
            parsed.add(toItem(items));
            return parsed;
        }
        for (JsonNode item : items) {
            parsed.add(toItem(item));
        }
        return parsed;
    }

    private static HubAttractionItem toItem(JsonNode node) {
        return new HubAttractionItem(
                node.path("hubRank").asInt(),
                node.path("hubTatsCd").asText(),
                node.path("hubTatsNm").asText(),
                textOrNull(node, "hubCtgryLclsNm"),
                textOrNull(node, "hubCtgryMclsNm"),
                doubleOrNull(node, "mapY"),
                doubleOrNull(node, "mapX")); // 데이터랩: mapX=경도, mapY=위도
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null; // 좌표가 깨졌으면 없는 것으로 — 지어내지 않는다
        }
    }
}
