package com.offway.core.trip.infrastructure.festival;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 전국문화축제표준데이터 adapter(#433) — {@code tn_pubr_public_cltur_fstvl_api}.
 *
 * <h2>필드명이 아직 실호출로 확정되지 않았다</h2>
 *
 * <p>포털 상세 페이지가 응답 항목의 <b>영문 명세를 공개하지 않는다</b>(한글 항목명만 표로 보여준다).
 * 아래 상수는 표준데이터 계열의 통상 표기를 따른 것이고, <b>실호출 한 번으로 확정해야 한다.</b>
 *
 * <p>그래서 <b>못 찾으면 조용히 넘어가지 않는다</b> — 응답에 행은 있는데 축제명을 하나도 못 읽으면
 * warn 을 남기고 빈 결과를 준다. 그러지 않으면 필드명이 틀렸을 때 "축제 0건" 이 정상처럼 보이고,
 * 그건 이 레포가 가장 경계하는 조용한 실패다.
 */
@Slf4j
@Component
class FestivalStandardClientImpl implements FestivalStandardClient {

    private static final String BASE = "https://api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api";

    /**
     * 한 건의 기본 상한. 표준데이터는 단순 조회라 TourAPI 계열(6초)과 같은 선에서 잡는다.
     *
     * <p><b>아직 실측하지 못했다.</b> 첫 실호출에서 응답시간 분포를 재고
     * {@code docs/external-api-inventory.md} 에 남긴 뒤 p99 기준으로 다시 정한다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String TYPE_JSON = "json";

    /** 성공 코드 — data.go.kr 계열이 "00" 과 "0000" 을 섞어 쓴다. */
    private static final List<String> SUCCESS_CODES = List.of("00", "0000");

    /** 날짜 형식 — 표준데이터는 {@code yyyy-MM-dd} 로 준다. 공백·빈칸이 섞여 오는 행이 있다. */
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── 응답 필드명 (실호출로 확정 필요) ────────────────────────────────
    private static final String F_NAME = "fstvlNm";
    private static final String F_START = "fstvlStartDate";
    private static final String F_END = "fstvlEndDate";
    private static final String F_CONTENT = "fstvlCo";
    private static final String F_VENUE = "opar";
    private static final String F_HOST = "mnnstNm";
    private static final String F_TEL = "phoneNumber";
    private static final String F_HOMEPAGE = "homepageUrl";
    private static final String F_ROAD_ADDRESS = "rdnmadr";
    private static final String F_JIBUN_ADDRESS = "lnmadr";
    private static final String F_LAT = "latitude";
    private static final String F_LNG = "longitude";

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    FestivalStandardClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public StandardFestivalResult findAll(int pageNo, int numOfRows, Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            log.info("문화축제표준데이터 키 없음 — 축제 조회를 건너뜁니다");
            return StandardFestivalResult.empty();
        }
        URI uri = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("type", TYPE_JSON)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .build(true)
                .toUri();
        Duration wait = maxWait.compareTo(TIMEOUT) < 0 ? maxWait : TIMEOUT;
        try {
            // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
            callRecorder.record(ExternalApi.FESTIVAL_STANDARD);
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parse(body, pageNo);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("문화축제표준데이터 조회 실패 page={} cause={}", pageNo, RootCause.of(e));
            throw TourApiException.festivalStandardLookupFailed(e);
        }
    }

    private StandardFestivalResult parse(String body, int pageNo) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode response = root.path("response");

        String resultCode = response.path("header").path("resultCode").asText();
        if (!resultCode.isEmpty() && !SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException("문화축제표준데이터 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }

        JsonNode bodyNode = response.path("body");
        int totalCount = bodyNode.path("totalCount").asInt(0);

        // 표준데이터 계열은 items 가 배열로 오기도 하고 {item:[...]} 로 한 겹 더 감싸 오기도 한다.
        JsonNode items = bodyNode.path("items");
        if (items.isObject()) {
            items = items.path("item");
        }
        if (items.isMissingNode() || items.isNull()) {
            return new StandardFestivalResult(List.of(), totalCount);
        }

        List<StandardFestival> parsed = new ArrayList<>();
        int rows = 0;
        for (JsonNode node : items.isArray() ? items : objectMapper.createArrayNode().add(items)) {
            rows++;
            StandardFestival festival = toFestival(node);
            if (festival != null) {
                parsed.add(festival);
            }
        }

        // **행은 왔는데 이름을 하나도 못 읽었다 = 필드명이 틀렸다.** 조용히 0건으로 넘기면 그 사실이
        // 어디에도 안 남는다. 위 클래스 주석의 "실호출로 확정 필요" 가 여기서 드러난다.
        if (rows > 0 && parsed.isEmpty()) {
            log.warn("문화축제표준데이터 {}행을 받았지만 한 건도 읽지 못했습니다 — 응답 필드명을 확인하세요"
                    + " (기대한 이름: {}) 실제 키: {}", rows, F_NAME, fieldNamesOf(items));
            return new StandardFestivalResult(List.of(), totalCount);
        }
        log.debug("문화축제표준데이터 page={} 받은행={} 읽은건={} 전체={}", pageNo, rows, parsed.size(), totalCount);
        return new StandardFestivalResult(parsed, totalCount);
    }

    /** 첫 행의 키 목록 — 필드명이 틀렸을 때 무엇으로 고쳐야 하는지 로그가 바로 답하게 한다. */
    private static String fieldNamesOf(JsonNode items) {
        JsonNode first = items.isArray() ? items.path(0) : items;
        List<String> names = new ArrayList<>();
        first.fieldNames().forEachRemaining(names::add);
        return String.join(", ", names);
    }

    /**
     * 한 행을 옮긴다. 필수값이 없거나 형식이 어긋나면 <b>그 한 건만</b> 건너뛴다(null).
     *
     * <p>좌표 없는 행이 446건 중 101건이라 흔한 경우이고, 그 한 건 때문에 전체 페이지를 502로 터뜨릴
     * 이유가 없다.
     */
    private static StandardFestival toFestival(JsonNode node) {
        String name = text(node, F_NAME);
        if (name == null) {
            return null;
        }
        String address = firstNonBlank(text(node, F_ROAD_ADDRESS), text(node, F_JIBUN_ADDRESS));
        StandardFestival festival = new StandardFestival(
                name,
                text(node, F_VENUE),
                address,
                sigunguOf(address),
                decimal(node, F_LAT),
                decimal(node, F_LNG),
                date(node, F_START),
                date(node, F_END),
                text(node, F_CONTENT),
                text(node, F_HOST),
                text(node, F_TEL),
                text(node, F_HOMEPAGE));
        return festival.isUsable() ? festival : null;
    }

    /**
     * 주소에서 시군구명을 뽑는다 — "경상북도 안동시 ..." 의 둘째 토큰.
     *
     * <p>지역 매칭에 쓰는 값이라 못 뽑으면 그 축제는 어느 지역에도 안 붙는다. 주소 체계가 "시도 시군구"
     * 로 시작하는 것은 도로명·지번 둘 다 같다.
     */
    private static String sigunguOf(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        return tokens.length >= 2 ? tokens[1] : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Double decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
