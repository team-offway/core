package com.offway.core.trip.infrastructure.datalab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.datalab.dto.RelatedAttractionItem;
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
 * 관광지별 연관 관광지 adapter(#186) — {@code TarRlteTarService1/areaBasedList1}.
 *
 * <p>같은 데이터랩 계열이라 {@link HubAttractionClientImpl} 과 함정을 공유한다.
 *
 * <ul>
 *   <li><b>법정동 코드를 쓴다.</b> 공주시는 {@code areaCd=44}·{@code signguCd=44150} 이다. TourAPI
 *       코드를 넣으면 {@code resultCode=0000} 에 결과 0건이 와서 <b>조용히 빈다.</b>
 *   <li><b>{@code pageNo} 가 필수다.</b> 빠뜨리면 {@code resultCode=11} 이 {@code response} 래퍼
 *       <b>없이</b> 최상위로 온다.
 *   <li>결과가 없으면 {@code items} 가 <b>빈 문자열</b>로 오고, 1건이면 {@code item} 이 배열이 아니라
 *       단일 객체다.
 * </ul>
 *
 * <h2>지역 밖을 여기서 거른다</h2>
 *
 * <p>원본은 인접 시군 것을 섞어 준다 — 실측(공주시)에서 300건 중 <b>45건(15%)</b> 이 천안·아산·부여
 * 였다. 온양온천역·천안종합버스터미널·롯데아울렛 부여점 같은 것들이다.
 *
 * <p><b>이 필터를 호출자에게 맡기지 않는다.</b> 한 곳만 잊어도 공주 코스에 천안 터미널이 들어가는데,
 * 그건 화면에 뜨기 전에는 아무도 모른다.
 *
 * <p>키가 없으면 외부 호출 없이 빈 목록(로컬 실행성).
 */
@Slf4j
@Component
class RelatedAttractionClientImpl implements RelatedAttractionClient {

    private static final String URL = "https://apis.data.go.kr/B551011/TarRlteTarService1/areaBasedList1";
    /**
     * 응답 상한 — <b>배치 전용이라 길게 잡는다</b>(#473).
     *
     * <p>요청 경로는 사용자가 기다리므로 6초가 맞다. 이 적재는 아무도 안 기다린다 — 느려도 받는 것이
     * 빈손으로 끝나는 것보다 낫다. 빈손이면 그날 데이터가 통째로 없다.
     *
     * <p>2026-09-06 게이트웨이 장애 때 이 값이 6초라 적재가 통째로 헛돌았다. 그날 부분 회복 구간을
     * 실측하니 <b>TLS 9.7초 · 첫 바이트 15.5초</b> 였다 — 6초로는 연결이 성립하기도 전에 포기한다.
     * CLAUDE.md 가 "timeout 은 median 이 아니라 응답시간 분포의 꼬리에서 정한다" 고 적은 그 자리다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
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

    RelatedAttractionClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public List<RelatedAttractionItem> findByRegion(
            String legalCode, String sigunguName, YearMonth baseMonth, int rows) {
        if (!props.dataGoKr().hasKey()) {
            log.info("데이터랩 키 없음 — 연관 관광지 조회를 건너뜁니다");
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
            return withinRegion(parse(call(builder)), sigunguName);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("연관 관광지 조회 실패 legalCode={} cause={}", legalCode, RootCause.of(e));
            throw TourApiException.lookupFailed(e);
        }
    }

    /**
     * 우리 지역 것만 남긴다 — <b>버린 수를 센다</b>.
     *
     * <p>조용히 거르면 원본이 갑자기 다른 지역만 주기 시작해도 모른다. 15% 는 정상이지만 100% 면
     * 코드를 잘못 넣은 것이다.
     */
    private static List<RelatedAttractionItem> withinRegion(
            List<RelatedAttractionItem> items, String sigunguName) {
        if (sigunguName == null || sigunguName.isBlank()) {
            // 우리 시드가 이상한 경우다. 거를 근거가 없으니 그대로 두되 남긴다.
            log.warn("지역명이 없어 연관 관광지의 지역 밖을 거르지 못합니다 — 받은 그대로 씁니다");
            return items;
        }
        List<RelatedAttractionItem> ours = items.stream()
                .filter(item -> sigunguName.equals(item.sigunguName()))
                .toList();
        int dropped = items.size() - ours.size();
        if (dropped > 0) {
            log.debug("연관 관광지에서 지역 밖 {}건을 뺐습니다 sigungu={} 남음={}",
                    dropped, sigunguName, ours.size());
        }
        if (!items.isEmpty() && ours.isEmpty()) {
            // 전부 지역 밖이면 코드를 잘못 넣었을 가능성이 크다 — 조용히 0건이 되지 않게.
            log.warn("연관 관광지 {}건이 전부 지역 밖입니다 — 법정동 코드를 확인하세요 sigungu={}",
                    items.size(), sigunguName);
        }
        return ours;
    }

    /**
     * 시도코드를 떼어낼 수 있는 법정동 코드인가 — <b>URI 를 만들기 전에</b> 본다.
     *
     * <p>{@code substring} 이 던지는 예외는 호출자의 지역별 격리를 뚫고 올라가 89곳 루프를 통째로
     * 중단시킨다. 같은 실패 경로에 태워 그 지역만 건너뛰게 한다.
     */
    private static void requireAreaCode(String legalCode) {
        if (legalCode == null || legalCode.length() < AREA_CODE_LENGTH) {
            log.warn("법정동 코드가 짧아 연관 관광지를 조회할 수 없습니다 legalCode={}", legalCode);
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

    private List<RelatedAttractionItem> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode response = root.path("response");
        // 오류는 response 래퍼 없이 최상위로 온다(pageNo 누락 → {"resultCode":"11", ...}).
        String resultCode = response.path("header").path("resultCode").asText(null);
        if (resultCode == null) {
            resultCode = root.path("resultCode").asText();
        }
        if (!SUCCESS_CODES.contains(resultCode)) {
            // 빈 목록으로 돌려주면 "미발행" 과 구분되지 않는다 — 실패가 발행 지연으로 읽힌다.
            log.warn("연관 관광지 조회가 실패 코드로 돌아왔습니다 resultCode={}", resultCode);
            throw new IllegalStateException("연관 관광지 조회 실패 코드: " + resultCode);
        }
        JsonNode itemsNode = response.path("body").path("items");
        // 결과가 없으면 items 가 빈 문자열로 온다(data.go.kr 함정).
        if (itemsNode.isTextual()) {
            return List.of();
        }
        JsonNode items = itemsNode.path("item");
        List<RelatedAttractionItem> parsed = new ArrayList<>();
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
     * <p>그대로 넘기면 한참 뒤 엔티티 생성 시점에 예외가 터지는데, 그 자리는 호출자의
     * {@code catch (TourApiException)} 이 못 잡는 곳이라 이상 데이터 한 건이 89곳 갱신을 멈춘다.
     */
    private static void addIfComplete(List<RelatedAttractionItem> parsed, JsonNode node) {
        RelatedAttractionItem item = toItem(node);
        if (!item.isComplete()) {
            log.warn("연관 관광지 항목에 필수 값이 없어 건너뜁니다 rank={} code={}", item.rank(), item.code());
            return;
        }
        parsed.add(item);
    }

    private static RelatedAttractionItem toItem(JsonNode node) {
        return new RelatedAttractionItem(
                text(node, "tAtsCd"),
                text(node, "tAtsNm"),
                text(node, "rlteTatsCd"),
                text(node, "rlteTatsNm"),
                node.path("rlteRank").asInt(0),
                text(node, "rlteCtgryLclsNm"),
                text(node, "rlteCtgryMclsNm"),
                text(node, "rlteSignguNm"));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
