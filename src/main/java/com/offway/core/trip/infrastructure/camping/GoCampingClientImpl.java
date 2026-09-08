package com.offway.core.trip.infrastructure.camping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsite;
import com.offway.core.trip.infrastructure.camping.dto.GoCampsiteResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 고캠핑(15101933) adapter(#510) — {@code B551011/GoCamping/basedList}.
 *
 * <h2>필드명을 실호출로 확정했다</h2>
 *
 * <p>축제(#506)에서 같은 계열의 필드명을 <b>추측했다가 틀렸다</b>({@code fstvlNm} vs 실제
 * {@code FSTVL_NM}). 통합 테스트는 stub 이 기대한 모양을 돌려줘 초록이었고, 운영에서 0건이 됐다.
 * 아래 상수는 2026-09-08 실호출로 확인한 값이고, {@code GoCampingClientE2ETest} 가 그것을 잠근다.
 *
 * <h2>응답이 커서 상한을 올린다</h2>
 *
 * <p>3,115건이 <b>7.3MB</b> 로 온다. 공용 클라이언트의 {@code maxInMemorySize} 가 2MB 라 그대로는
 * 못 받는다. 페이지로 나누는 대신 {@code mutate()} 로 이 어댑터의 상한만 올렸다 — 그러면 공용 빈에
 * 붙은 계측(호출 로깅·외부 상태 관찰)을 그대로 물려받는다.
 *
 * <p>나누지 않는 이유는 호출 수만큼 실패 지점이 늘고 "일부만 받은 회차" 라는 상태가 생기기 때문이다.
 * 월 1회 배치가 잠깐 쓰는 메모리라 그 복잡도를 살 이유가 없다.
 *
 * <h2>못 읽으면 던진다</h2>
 *
 * <p>행은 왔는데 야영장명을 하나도 못 읽으면 <b>던진다</b>. 빈 결과를 돌려주면 호출자에게는 "성공한
 * 빈 회차" 로 보이고, 그러면 정리가 이번 회차를 온전한 것으로 판정해 <b>멀쩡한 야영장을 지운다</b>.
 */
@Slf4j
@Component
class GoCampingClientImpl implements GoCampingClient {

    private static final String BASE = "https://apis.data.go.kr/B551011/GoCamping/basedList";

    /**
     * 한 요청에 담는 건수 — 전국 3,115건이 한 번에 온다.
     *
     * <p>원본이 늘어도 견디게 여유를 뒀다. 실제로 받은 수가 {@code totalCount} 에 못 미치면 아래에서
     * 경고를 남긴다 — 그때 이 값을 다시 본다.
     */
    private static final int ROWS = 4_000;

    /** 응답 상한 — 실측 7.3MB. 원본이 두 배로 늘어도 견딘다. */
    private static final int MAX_IN_MEMORY = 16 * 1024 * 1024;

    /**
     * 한 호출의 상한.
     *
     * <p>실측 3.1초다(2026-09-08). 7MB 를 받는 조회라 TourAPI 단건(6초)보다 넉넉히 잡는다 — 월 1회
     * 배치라 여기서 아껴 봐야 얻는 것이 없고, 짧게 잘라 실패하면 그달 내내 야영장을 모른다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final String TYPE_JSON = "json";

    /** 성공 코드 — data.go.kr 계열이 "00" 과 "0000" 을 섞어 쓴다. */
    private static final List<String> SUCCESS_CODES = List.of("00", "0000");

    /** 운영 상태 중 <b>담을 수 있는 것</b>. 나머지("휴장" 123건)는 코스에 넣으면 헛걸음이다. */
    private static final String STATUS_OPERATING = "운영";

    // ── 응답 필드명 (2026-09-08 실호출로 확정) ──────────────────────────
    private static final String F_ID = "contentId";
    private static final String F_NAME = "facltNm";
    private static final String F_ADDRESS = "addr1";
    private static final String F_SIGUNGU = "sigunguNm";
    private static final String F_LAT = "mapY";
    private static final String F_LNG = "mapX";
    private static final String F_INDUTY = "induty";
    private static final String F_IMAGE = "firstImageUrl";
    private static final String F_LINE_INTRO = "lineIntro";
    private static final String F_INTRO = "intro";
    private static final String F_TEL = "tel";
    private static final String F_HOMEPAGE = "homepage";
    private static final String F_OPER_PERIOD = "operPdCl";
    private static final String F_OPER_DAYS = "operDeCl";
    private static final String F_RESERVATION = "resveCl";
    private static final String F_STATUS = "manageSttus";

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    GoCampingClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        // 공용 빈의 필터(호출 로깅·외부 상태 관찰)를 물려받고 본문 상한만 올린다.
        this.webClient = externalWebClient.mutate()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY))
                .build();
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public GoCampsiteResult findAll(Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            log.info("고캠핑 키 없음 — 야영장 조회를 건너뜁니다");
            return GoCampsiteResult.empty();
        }
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)).
        URI uri = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", TYPE_JSON)
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .build(true)
                .toUri();
        Duration wait = maxWait.compareTo(TIMEOUT) < 0 ? maxWait : TIMEOUT;
        try {
            // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
            callRecorder.record(ExternalApi.GO_CAMPING);
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parse(body);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("고캠핑 야영장 조회 실패 cause={}", RootCause.label(e));
            throw TourApiException.campingLookupFailed(e);
        }
    }

    private GoCampsiteResult parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");

        String resultCode = response.path("header").path("resultCode").asText();
        if (!resultCode.isEmpty() && !SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException("고캠핑 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }

        JsonNode bodyNode = response.path("body");
        int totalCount = bodyNode.path("totalCount").asInt(0);

        // data.go.kr 계열은 결과가 없으면 items 가 빈 문자열로 오고, 한 건이면 item 이 단일 객체다.
        JsonNode items = bodyNode.path("items");
        if (items.isObject()) {
            items = items.path("item");
        }
        if (items.isMissingNode() || items.isNull() || items.isTextual()) {
            return new GoCampsiteResult(List.of(), totalCount);
        }

        List<GoCampsite> usable = new ArrayList<>();
        int rows = 0;
        int namedRows = 0;
        int closed = 0;
        for (JsonNode node : items.isArray() ? items : objectMapper.createArrayNode().add(items)) {
            rows++;
            GoCampsite campsite = toCampsite(node);
            if (campsite.name() == null) {
                continue; // 이름조차 못 읽었다 — 필드명 오류 후보다. 아래에서 함께 판정한다
            }
            namedRows++;
            if (!campsite.operating()) {
                closed++;
                continue;
            }
            if (campsite.isUsable()) {
                usable.add(campsite);
            }
        }

        // **행은 왔는데 이름을 하나도 못 읽었다 = 필드명이 틀렸다.** 축제가 정확히 이렇게 조용히 0건이
        // 됐다(#506). 던져야 호출자가 실패로 세고, 정리가 통째로 건너뛰어진다.
        if (rows > 0 && namedRows == 0) {
            throw new IllegalStateException(
                    "고캠핑 %d행을 받았지만 야영장명을 하나도 읽지 못했습니다 — 응답 필드명을 확인하세요 (기대한 이름: %s) 실제 키: %s"
                            .formatted(rows, F_NAME, fieldNamesOf(items)));
        }
        if (rows < totalCount) {
            // 조용히 자르지 않는다. 한 번에 다 온다는 전제가 깨진 것이라 ROWS 를 다시 봐야 한다.
            log.warn("고캠핑이 말한 전체는 {}건인데 {}행만 받았습니다 — 한 요청 건수 상한({})을 다시 보세요",
                    totalCount, rows, ROWS);
        }
        log.info("고캠핑 조회 받은행={} 전체={} 휴장={} 쓸수있음={}", rows, totalCount, closed, usable.size());
        return new GoCampsiteResult(usable, totalCount);
    }

    /** 첫 행의 키 목록 — 필드명이 틀렸을 때 무엇으로 고쳐야 하는지 로그가 바로 답하게 한다. */
    private static String fieldNamesOf(JsonNode items) {
        JsonNode first = items.isArray() ? items.path(0) : items;
        List<String> names = new ArrayList<>();
        first.fieldNames().forEachRemaining(names::add);
        return String.join(", ", names);
    }

    /**
     * 한 행을 옮긴다 — <b>거르지 않고 그대로</b>. 쓸 수 있는지는 호출자가
     * {@link GoCampsite#isUsable()} 로 묻는다.
     *
     * <p>여기서 걸러 null 을 돌려주면 "이름을 못 읽음"(필드명 오류)과 "휴장이라 뺌"(정상)이 같은 값이
     * 돼 구분할 수 없다. 그 둘이 갈려야 조용한 실패를 잡는다.
     */
    private static GoCampsite toCampsite(JsonNode node) {
        return new GoCampsite(
                text(node, F_ID),
                text(node, F_NAME),
                text(node, F_ADDRESS),
                text(node, F_SIGUNGU),
                decimal(node, F_LAT),
                decimal(node, F_LNG),
                text(node, F_INDUTY),
                text(node, F_IMAGE),
                text(node, F_LINE_INTRO),
                text(node, F_INTRO),
                text(node, F_TEL),
                text(node, F_HOMEPAGE),
                text(node, F_OPER_PERIOD),
                text(node, F_OPER_DAYS),
                text(node, F_RESERVATION),
                STATUS_OPERATING.equals(text(node, F_STATUS)));
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
}
