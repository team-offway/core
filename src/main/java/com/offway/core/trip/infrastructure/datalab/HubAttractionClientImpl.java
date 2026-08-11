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
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기초지자체 중심 관광지 adapter — {@code LocgoHubTarService1/areaBasedList1}.
 *
 * <p><b>코드 체계가 TourAPI 와 다르다.</b> 데이터랩은 <b>법정동 코드</b>를 쓴다 — 공주시는 {@code areaCd=44},
 * {@code signguCd=44150} 이다. TourAPI 코드(34/1)를 넣으면 {@code resultCode=0000} 에 {@code totalCount=0} 이
 * 돌아온다. 성공 코드라 조용히 빈 결과가 되므로 특히 조심해야 한다.
 *
 * <p><b>{@code pageNo} 가 필수다.</b> 빠뜨리면 {@code resultCode=11}(NO_MANDATORY_REQUEST_PARAMETERS_ERROR)이
 * 오는데, 그 응답은 {@code response} 래퍼 <b>없이</b> 최상위에 코드만 담아 온다. 래퍼만 보고 파싱하면 코드가 빈
 * 문자열이 돼 <b>결과 없음과 구분되지 않는다</b> — 실제로 조사 중 이걸로 "최신 월이 없다" 고 잘못 읽었다.
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
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    HubAttractionClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public List<HubAttractionItem> findByRegion(String legalCode, YearMonth baseMonth, int rows) {
        if (!props.dataGoKr().hasKey()) {
            log.info("데이터랩 키 없음 — 중심 관광지 조회를 건너뜁니다");
            return List.of();
        }
        requireAreaCode(legalCode);
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

    /**
     * 시도코드를 떼어낼 수 있는 법정동 코드인가 — <b>URI 를 만들기 전에</b> 본다.
     *
     * <p>{@code substring} 이 던지는 NPE·{@code StringIndexOutOfBoundsException} 는
     * {@link TourApiException} 이 아니라, 호출자({@code HubAttractionRefreshService})의 지역별 격리를
     * 뚫고 올라가 <b>89곳 루프를 통째로 중단</b>시킨다. legal_code 가 빈 지역 하나가 나머지 88곳의 갱신을
     * 막는 셈이라, 같은 실패 경로에 태워 그 지역만 건너뛰게 한다.
     */
    private static void requireAreaCode(String legalCode) {
        if (legalCode == null || legalCode.length() < AREA_CODE_LENGTH) {
            // 외부가 아니라 우리 시드가 원인이다 — 서비스 쪽 로그는 cause 클래스명만 남기므로 여기서 밝힌다.
            log.warn("법정동 코드가 짧아 중심 관광지를 조회할 수 없습니다 legalCode={}", legalCode);
            throw TourApiException.lookupFailed(
                    new IllegalArgumentException("법정동 코드가 시도코드를 담기에 짧습니다: " + legalCode));
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(#165).
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.TOUR_DATA_LAB);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private List<HubAttractionItem> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode response = root.path("response");
        // 오류는 response 래퍼 <b>없이</b> 최상위로 온다(실측: pageNo 누락 → {"resultCode":"11", ...}).
        // 래퍼만 보면 코드가 빈 문자열이 돼, 실패가 "결과 없음" 과 구분되지 않는다.
        String resultCode = response.path("header").path("resultCode").asText(null);
        if (resultCode == null) {
            resultCode = root.path("resultCode").asText();
        }
        if (!SUCCESS_CODES.contains(resultCode)) {
            // 빈 목록으로 돌려주면 "미발행" 과 구분되지 않는다 — 할당량 초과·파라미터 오류가 발행 지연으로
            // 읽혀 발행월 탐색이 이전 달들을 헛되이 순회하고, 호출자의 집계에도 실패가 아니라 빈 응답으로
            // 잡힌다. 조회 실패로 올려 그 지역만 이전 값을 유지하게 한다.
            log.warn("중심 관광지 조회가 실패 코드로 돌아왔습니다 resultCode={}", resultCode);
            throw new IllegalStateException("중심 관광지 조회 실패 코드: " + resultCode);
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
            addIfComplete(parsed, items);
            return parsed;
        }
        for (JsonNode item : items) {
            addIfComplete(parsed, item);
        }
        return parsed;
    }

    /**
     * 필수 값이 빠진 항목은 <b>어댑터에서</b> 버린다.
     *
     * <p>{@code path(...).asInt()} 는 필드가 없으면 0을, {@code asText()} 는 빈 문자열을 준다. 그대로 넘기면
     * 한참 뒤 {@code toEntity()} 시점에 {@link IllegalArgumentException} 이 터지는데, 그 자리는 호출자의
     * {@code catch (TourApiException)} 이 못 잡는 곳이라 <b>이상 데이터 한 건이 89곳 갱신을 통째로 멈춘다.</b>
     *
     * <p>스키마가 어긋난 것은 여기서 알아야 할 사건이므로 warn 을 남긴다 — 조용히 버리면 순위에 구멍이 나도 모른다.
     */
    private static void addIfComplete(List<HubAttractionItem> parsed, JsonNode node) {
        HubAttractionItem item = toItem(node);
        if (!item.isComplete()) {
            log.warn("중심 관광지 항목에 필수 값이 없어 건너뜁니다 rank={} code={}", item.rank(), item.code());
            return;
        }
        parsed.add(item);
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
