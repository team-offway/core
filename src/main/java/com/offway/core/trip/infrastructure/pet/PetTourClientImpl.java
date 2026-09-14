package com.offway.core.trip.infrastructure.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.DataGoKrError;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.common.logging.SensitiveParams;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourPlace;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 반려동물 동반여행 adapter(#566) — {@code B551011/KorPetTourService2}.
 *
 * <h2>필드명을 실호출로 확정했다</h2>
 *
 * <p>축제(#506)에서 같은 계열의 필드명을 <b>추측했다가 틀렸다</b>. 통합 테스트는 stub 이 기대한 모양을
 * 돌려줘 초록이었고 운영에서 0건이 됐다. 아래 상수는 2026-09-13 실호출로 확인한 값이다.
 *
 * <p><b>목록과 상세의 표기가 다르다.</b> 목록은 소문자(`contentid`)이고 상세는 카멜케이스(`contentid` +
 * `acmpyTypeCd`)다. 같은 서비스인데도 그렇다 — 한쪽 표기로 통일해 읽으면 조용히 0건이 된다.
 *
 * <h2>지역 파라미터를 넣지 않는다</h2>
 *
 * <p>이 API 만 요청에 TourAPI 지역코드를 쓴다(#555 함정 3). 그런데 <b>지역을 빼면 전국이 오고</b>,
 * 응답에 법정동 코드가 함께 담겨 우리 89곳과 직접 맞출 수 있다. 89콜이 1콜로 줄고 매칭도 정확해진다
 * (170 → 442건).
 *
 * <h2>응답이 커서 상한을 올린다</h2>
 *
 * <p>9,679건이 <b>6.3MB</b> 로 온다. 공용 클라이언트의 {@code maxInMemorySize} 로는 못 받으므로
 * {@code mutate()} 로 이 어댑터의 상한만 올렸다 — 그러면 공용 빈에 붙은 계측(호출 로깅·외부 상태 관찰)을
 * 그대로 물려받는다(고캠핑과 같은 방식).
 *
 * <h2>못 읽으면 던진다</h2>
 *
 * <p>행은 왔는데 장소명을 하나도 못 읽으면 <b>던진다</b>. 빈 결과를 돌려주면 호출자에게는 "성공한 빈
 * 회차" 로 보이고, 그러면 정리가 이번 회차를 온전한 것으로 판정해 <b>멀쩡한 표식을 지운다</b>.
 */
@Slf4j
@Component
class PetTourClientImpl implements PetTourClient {

    private static final String BASE = "https://apis.data.go.kr/B551011/KorPetTourService2";
    private static final String PATH_LIST = "/areaBasedList2";
    private static final String PATH_DETAIL = "/detailPetTour2";

    /**
     * 한 요청에 담는 건수 — 전국 9,679건이 한 번에 온다.
     *
     * <p>원본이 늘어도 견디게 여유를 뒀다. 받은 수가 {@code totalCount} 와 어긋나면 아래에서 던진다.
     */
    private static final int ROWS = 12_000;

    /** 응답 상한 — 실측 6.3MB. 원본이 두 배로 늘어도 견딘다. */
    private static final int MAX_IN_MEMORY = 16 * 1024 * 1024;

    /**
     * 목록 한 호출의 상한.
     *
     * <p>실측 2.8초다. 6MB 를 받는 조회라 넉넉히 잡는다 — 월 1회 배치라 여기서 아껴 봐야 얻는 것이 없고,
     * 짧게 잘라 실패하면 그달 내내 반려동반 표식을 모른다.
     */
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 상세 한 호출의 상한 — 단건이라 짧다.
     *
     * <p>442건을 병렬로 도는 경로라 하나가 길게 물고 있으면 전체가 늦어진다. 단건 조회의 꼬리를 보고
     * 잡되, 실패한 건은 조건 없는 표식으로 남으므로(칩은 뜨고 상세만 빈다) 여기서 관대할 이유가 없다.
     */
    private static final Duration DETAIL_TIMEOUT = Duration.ofSeconds(10);

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final String TYPE_JSON = "json";

    /** 성공 코드 — data.go.kr 계열이 "00" 과 "0000" 을 섞어 쓴다. */
    private static final List<String> SUCCESS_CODES = List.of("00", "0000");

    // ── 목록 응답 필드명 (2026-09-13 실호출로 확정 · 전부 소문자) ──────────
    private static final String F_LIST_ID = "contentid";
    private static final String F_LIST_TITLE = "title";
    private static final String F_LIST_REGN = "lDongRegnCd";
    private static final String F_LIST_SIGNGU = "lDongSignguCd";

    // ── 상세 응답 필드명 (2026-09-13 실호출로 확정) ────────────────────────
    private static final String F_DETAIL_ID = "contentid";
    private static final String F_ACCOMPANY_AREA = "acmpyTypeCd";
    private static final String F_ACCOMPANY_PET = "acmpyPsblCpam";
    private static final String F_REQUIRED_MATTER = "acmpyNeedMtr";
    private static final String F_ETC_INFO = "etcAcmpyInfo";
    private static final String F_RISK_MATTER = "relaAcdntRiskMtr";
    private static final String F_FACILITIES = "relaPosesFclty";
    private static final String F_PROVIDED_ITEMS = "relaFrnshPrdlst";
    private static final String F_RENTAL_ITEMS = "relaRntlPrdlst";

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    PetTourClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        // 공용 빈의 필터(호출 로깅·외부 상태 관찰)를 물려받고 본문 상한만 올린다.
        this.webClient = externalWebClient.mutate()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY))
                .build();
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public PetTourResult findAll(Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            log.info("반려동반 키 없음 — 장소 조회를 건너뜁니다");
            return PetTourResult.empty();
        }
        URI uri = listUri();
        Duration wait = shorterOf(maxWait, LIST_TIMEOUT);
        try {
            // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
            callRecorder.record(ExternalApi.PET_TOUR);
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parseList(body);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("반려동반 장소 조회 실패 cause={}", RootCause.label(e));
            throw TourApiException.petTourLookupFailed(e);
        }
    }

    @Override
    public Optional<PetTourDetail> findDetail(String contentId, Duration maxWait) {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        Duration wait = shorterOf(maxWait, DETAIL_TIMEOUT);
        try {
            callRecorder.record(ExternalApi.PET_TOUR);
            String body = webClient.get()
                    .uri(detailUri(contentId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parseDetail(body, contentId);
        } catch (Exception e) {
            // **던지지 않는다.** 상세 하나가 실패해도 그 장소는 "조건을 모르는 반려동반 장소" 로 남으면
            // 되고, 442건을 도는 회차가 한 건 때문에 통째로 실패하면 아무 표식도 못 남긴다.
            // 호출자가 실패 수를 세어 로그로 드러낸다.
            log.warn("반려동반 상세 조회 실패 contentId={} cause={}",
                    SensitiveParams.forLog(contentId), RootCause.label(e));
            return Optional.empty();
        }
    }

    /** serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다({@code build(true)}). */
    private URI listUri() {
        return UriComponentsBuilder.fromUriString(BASE + PATH_LIST)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", TYPE_JSON)
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .build(true)
                .toUri();
    }

    private URI detailUri(String contentId) {
        return UriComponentsBuilder.fromUriString(BASE + PATH_DETAIL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", TYPE_JSON)
                .queryParam("contentId", contentId)
                .build(true)
                .toUri();
    }

    private PetTourResult parseList(String body) throws Exception {
        JsonNode bodyNode = successBodyOf(body);
        int totalCount = bodyNode.path("totalCount").asInt(0);
        JsonNode items = itemsOf(bodyNode);
        if (items == null) {
            return new PetTourResult(List.of(), totalCount);
        }

        List<PetTourPlace> matchable = new ArrayList<>();
        int rows = 0;
        int namedRows = 0;
        for (JsonNode node : arrayOf(items)) {
            rows++;
            PetTourPlace place = toPlace(node);
            if (place.title() == null) {
                continue; // 이름조차 못 읽었다 — 필드명 오류 후보다. 아래에서 함께 판정한다
            }
            namedRows++;
            if (place.isMatchable()) {
                matchable.add(place);
            }
        }

        // **행은 왔는데 이름을 하나도 못 읽었다 = 필드명이 틀렸다.** 축제가 정확히 이렇게 조용히 0건이
        // 됐다(#506). 던져야 호출자가 실패로 세고, 정리가 통째로 건너뛰어진다.
        if (rows > 0 && namedRows == 0) {
            throw new IllegalStateException(
                    "반려동반 %d행을 받았지만 장소명을 하나도 읽지 못했습니다 — 응답 필드명을 확인하세요 (기대한 이름: %s)"
                            .formatted(rows, F_LIST_TITLE));
        }
        // **불완전한 결과를 성공으로 돌려주지 않는다.** 호출자는 비어 있지 않은 결과를 정상 회차로 보고
        // 이번에 안 온 장소의 표식을 지운다 — 첫 페이지 밖이 통째로 사라진다. 고캠핑과 같은 판정이다.
        if (rows > 0 && rows != totalCount) {
            throw new IllegalStateException(
                    "반려동반이 말한 전체(%d)와 받은 행(%d)이 다릅니다 — 잘렸으면 한 요청 건수 상한(%d)을, 전체가 더 작으면 응답 형식을 확인하세요"
                            .formatted(totalCount, rows, ROWS));
        }
        log.info("반려동반 전국 조회 전체={}건 매칭가능={}건", totalCount, matchable.size());
        return new PetTourResult(matchable, totalCount);
    }

    /**
     * 상세 응답에서 <b>물어본 그 장소</b>의 조건을 꺼낸다 — 응답 순서를 믿지 않고 id 로 고른다.
     *
     * <p>첫 항목을 그대로 쓰면 다른 장소의 항목이 앞에 왔을 때 그 조건이 이 장소에 붙는다. 여기서
     * 틀리면 "일부구역 동반가능 · 5kg 제한" 같은 조건이 엉뚱한 곳에 뜨고, 사용자는 그걸 믿고 갔다가
     * 못 들어간다. 목록에서 행 수와 전체 수를 대조하는 것과 같은 이유다 — 조용히 틀리게 두지 않는다.
     */
    private Optional<PetTourDetail> parseDetail(String body, String requestedId) throws Exception {
        JsonNode bodyNode = successBodyOf(body);
        JsonNode items = itemsOf(bodyNode);
        if (items == null) {
            return Optional.empty();
        }
        for (JsonNode node : arrayOf(items)) {
            String contentId = text(node, F_DETAIL_ID);
            if (contentId == null || !contentId.equals(requestedId)) {
                continue;
            }
            return Optional.of(new PetTourDetail(
                    contentId,
                    text(node, F_ACCOMPANY_AREA),
                    text(node, F_ACCOMPANY_PET),
                    text(node, F_REQUIRED_MATTER),
                    text(node, F_ETC_INFO),
                    text(node, F_RISK_MATTER),
                    text(node, F_FACILITIES),
                    text(node, F_PROVIDED_ITEMS),
                    text(node, F_RENTAL_ITEMS)));
        }
        return Optional.empty();
    }

    /**
     * 성공을 <b>확인하고</b> 본문을 꺼낸다 — 성공 코드가 없으면 성공이 아니다(#569).
     *
     * <p>예전에는 코드가 비어 있으면 통과시켰다. 그런데 인증키가 막히면 {@code response} 키가 없는
     * 다른 envelope 이 와서 코드가 빈 문자열로 읽힌다 — 그대로 통과하면 목록이 0건이 되고, 호출자는
     * 그것을 "받아 왔는데 없더라" 로 읽어 회차를 건너뛴다. <b>한도가 마른 회차와 정말로 0건인 회차가
     * 로그에서 같아 보인다.</b>
     *
     * <p>정상 0건도 {@code resultCode=0000} 을 준다(실측: 없는 지역코드·범위 밖 페이지 모두). 코드를
     * 요구해도 멀쩡한 회차가 실패로 뒤집히지 않는다.
     */
    private JsonNode successBodyOf(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException(
                    "반려동반 응답이 성공이 아닙니다: resultCode=%s%s"
                            .formatted(resultCode.isEmpty() ? "없음" : resultCode, DataGoKrError.of(root)));
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

    private PetTourPlace toPlace(JsonNode node) {
        return new PetTourPlace(
                text(node, F_LIST_ID),
                text(node, F_LIST_TITLE),
                legalCodeOf(text(node, F_LIST_REGN), text(node, F_LIST_SIGNGU)));
    }

    /**
     * 법정 시군구코드 5자리 — 시도 2자리 + 시군구 3자리.
     *
     * <p>실측에서 9,679건 중 4건이 한쪽을 비우고 왔다. 그건 어느 지역인지 모르는 것이라 <b>이어 붙여
     * 만들어내지 않는다</b> — 잘못된 코드는 엉뚱한 지역의 장소에 칩을 붙인다.
     */
    private static String legalCodeOf(String regionCode, String sigunguCode) {
        if (regionCode == null || sigunguCode == null) {
            return null;
        }
        return regionCode + sigunguCode;
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
