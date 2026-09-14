package com.offway.core.trip.infrastructure.crowd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.common.logging.SensitiveParams;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.crowd.dto.AttractionCrowd;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 관광지 집중률 예측 어댑터(#565).
 *
 * <p><b>지역 하나를 한 요청으로 받는다.</b> {@code numOfRows} 를 키우면 그 지역의 관광지 × 30일이 한 번에
 * 온다. 한도는 요청 수라, 89곳을 매일 돌아도 89콜이다.
 *
 * <p>본문 상한만 올린 {@link WebClient} 를 쓴다 — 실측 최대 432KB(태안, 2,910행)로 기본값 256KB 를
 * 넘는다. 공용 빈을 {@code mutate()} 해 만들므로 호출 로깅·외부 상태 관찰 필터는 그대로 물려받는다.
 */
@Slf4j
@Component
class AttractionCrowdClientImpl implements AttractionCrowdClient {

    private static final String BASE = "https://apis.data.go.kr/B551011/TatsCnctrRateService";
    private static final String PATH_LIST = "/tatsCnctrRatedList";

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final String TYPE_JSON = "json";

    /**
     * 한 요청에 받는 행 수 — 그 지역 전량이 한 번에 오도록 넉넉히.
     *
     * <p>실측 최대가 태안 2,910행(관광지 97개 × 30일)이다. 관광지가 300개까지 늘어도 한 번에 온다.
     */
    private static final int ROWS = 9_000;

    /**
     * 본문 상한 — 실측 최대 432KB 의 아홉 배.
     *
     * <p>기본값 256KB 로는 큰 지역이 통째로 실패한다. 상한을 올리는 것은 <b>이 어댑터에서만</b> 한다 —
     * 공용 빈을 올리면 다른 외부 응답까지 같은 메모리를 쓸 수 있게 된다.
     */
    private static final int MAX_IN_MEMORY = 4 * 1024 * 1024;

    /** 지역 하나의 상한. 432KB 를 받는 호출이라 목록치고 넉넉히 둔다. */
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(30);

    /** data.go.kr 이 성공으로 쓰는 코드 — 계열마다 표기가 갈려 둘 다 받는다. */
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");

    private static final DateTimeFormatter BASE_YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String F_NAME = "tAtsNm";
    private static final String F_DATE = "baseYmd";
    private static final String F_RATE = "cnctrRate";

    /** 법정 시군구코드 앞 2자리가 시도다 — {@code areaCd} 가 그 값이다. */
    private static final int SIDO_CODE_LENGTH = 2;

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AttractionCrowdClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient.mutate()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY))
                .build();
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public List<AttractionCrowd> findByRegion(String legalCode, Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            log.info("집중률 키 없음 — 조회를 건너뜁니다");
            return List.of();
        }
        Duration wait = shorterOf(maxWait, LIST_TIMEOUT);
        try {
            // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
            callRecorder.record(ExternalApi.TATS_CROWD_RATE);
            String body = webClient.get()
                    .uri(listUri(legalCode))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parse(body);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("집중률 조회 실패 legalCode={} cause={}",
                    SensitiveParams.forLog(legalCode), RootCause.label(e));
            throw TourApiException.crowdRateLookupFailed(e);
        }
    }

    /** serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다({@code build(true)}). */
    private URI listUri(String legalCode) {
        return UriComponentsBuilder.fromUriString(BASE + PATH_LIST)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", TYPE_JSON)
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("areaCd", legalCode.substring(0, SIDO_CODE_LENGTH))
                .queryParam("signguCd", legalCode)
                .build(true)
                .toUri();
    }

    private List<AttractionCrowd> parse(String body) throws Exception {
        JsonNode bodyNode = successBodyOf(body);
        int totalCount = bodyNode.path("totalCount").asInt(0);
        JsonNode items = itemsOf(bodyNode);
        if (items == null) {
            return List.of();
        }

        List<AttractionCrowd> parsed = new ArrayList<>();
        int rows = 0;
        int namedRows = 0;
        for (JsonNode node : arrayOf(items)) {
            rows++;
            String name = text(node, F_NAME);
            if (name == null) {
                continue; // 이름조차 못 읽었다 — 필드명 오류 후보다. 아래에서 함께 판정한다
            }
            namedRows++;
            LocalDate date = dateOf(text(node, F_DATE));
            Double rate = rateOf(text(node, F_RATE));
            if (date != null && rate != null) {
                parsed.add(new AttractionCrowd(name, date, rate));
            }
        }

        // **행은 왔는데 이름을 하나도 못 읽었다 = 필드명이 틀렸다.** 축제가 정확히 이렇게 조용히 0건이
        // 됐다(#506). 던져야 호출자가 실패로 세고, 그 지역 갱신을 건너뛴다.
        if (rows > 0 && namedRows == 0) {
            throw new IllegalStateException(
                    "집중률 %d행을 받았지만 관광지명을 하나도 읽지 못했습니다 — 응답 필드명을 확인하세요 (기대한 이름: %s)"
                            .formatted(rows, F_NAME));
        }
        // **불완전한 결과를 성공으로 돌려주지 않는다.** 뒷부분이 잘린 채 저장하면 그 관광지들이 "예보가
        // 없는 곳" 이 되어 칩이 조용히 사라진다(#566 과 같은 판정).
        if (rows > 0 && rows != totalCount) {
            throw new IllegalStateException(
                    "집중률이 말한 전체(%d)와 받은 행(%d)이 다릅니다 — 잘렸으면 한 요청 건수 상한(%d)을 확인하세요"
                            .formatted(totalCount, rows, ROWS));
        }
        return parsed;
    }

    private JsonNode successBodyOf(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        String resultCode = response.path("header").path("resultCode").asText();
        if (!resultCode.isEmpty() && !SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException("집중률 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }
        return response.path("body");
    }

    /**
     * 결과 목록 노드 — 없으면 null.
     *
     * <p>data.go.kr 계열은 결과가 없으면 {@code items} 가 <b>빈 문자열</b>로 오고, 한 건이면
     * {@code item} 이 단일 객체다. 둘 다 배열로 다루면 터진다.
     */
    private JsonNode itemsOf(JsonNode bodyNode) {
        JsonNode items = bodyNode.path("items");
        if (items.isObject()) {
            items = items.path("item");
        }
        if (items.isMissingNode() || items.isNull() || items.isTextual()) {
            return null;
        }
        return items;
    }

    private Iterable<JsonNode> arrayOf(JsonNode items) {
        return items.isArray() ? items : objectMapper.createArrayNode().add(items);
    }

    /** {@code yyyyMMdd} 가 아니면 그 행을 버린다 — 날짜를 못 읽으면 어느 날 예보인지 모른다. */
    private static LocalDate dateOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, BASE_YMD);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double rateOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text.trim();
    }

    private static Duration shorterOf(Duration left, Duration right) {
        return left.compareTo(right) < 0 ? left : right;
    }
}
