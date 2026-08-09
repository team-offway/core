package com.offway.core.trip.infrastructure.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.retry.Retry;

/**
 * 국문 관광정보(TourAPI · KorService2) adapter.
 *
 * <p>키가 없으면 외부 호출 없이 빈 결과를 돌려준다(로컬 실행성 불변식). 호출·파싱 실패는 {@link TourApiException}(502)으로 올린다.
 * data.go.kr 함정(성공코드 아닌데 items 없음, 1건일 때 item 이 단일 객체, 결과 없으면 items 가 빈 문자열)을 방어한다.
 */
@Slf4j
@Component
class TourApiClientImpl implements TourApiClient {

    private static final String BASE = "https://apis.data.go.kr/B551011/KorService2";
    private static final String WITH_BASE = "https://apis.data.go.kr/B551011/KorWithService2";
    private static final String AREA_BASED = "/areaBasedList2";
    private static final String LOCATION_BASED = "/locationBasedList2";
    private static final String DETAIL_INTRO = "/detailIntro2";
    private static final String DETAIL_COMMON = "/detailCommon2";
    private static final String DETAIL_WITH_TOUR = "/detailWithTour2";

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /**
     * 429 재시도 횟수·간격.
     *
     * <p><b>왜 필요한가.</b> 부팅 워밍이 89개 지역을 도는데, 동시성 상한(12)만으로는 <b>초당 호출 수</b>가 안 잡힌다.
     * 실측(배포 로그)에서 200ms 안에 18건이 나가 초당 90건꼴이었고 제공기관이 429 를 던졌다.
     *
     * <p>그리고 <b>429 는 즉시 돌아온다</b> — 정상 응답은 수백 ms 걸리는데 실패는 10ms 안에 떨어지므로, 실패할수록
     * 다음 호출이 더 빨리 나가 429 를 더 맞는 되먹임이 생긴다. 백오프가 그 고리를 끊는다: 실패한 워커가 쉬는 동안
     * 전체 호출 속도가 저절로 내려간다.
     *
     * <p>지터를 넣는 이유는 12개 워커가 <b>같은 순간에</b> 깨어나 다시 몰리지 않게 하기 위해서다.
     *
     * <p>재시도는 <b>429 에만</b> 건다. timeout·5xx 는 이미 느린 상황이라 다시 걸면 지연만 곱해진다.
     */
    private static final int RATE_LIMIT_RETRIES = 2;

    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMillis(400);

    private static final double RATE_LIMIT_JITTER = 0.5;

    /**
     * 재시도까지 <b>포함한</b> 한 호출의 상한.
     *
     * <p>{@link #TIMEOUT} 은 시도 하나에만 걸린다. 재시도가 붙으면 재구독되므로 전체는 (시도 × 횟수 + 백오프)
     * 까지 늘어난다 — 429 가 늦게 도착하는 경우 최악 20초에 가깝다. 그러면 팬아웃의 전체 상한을 넘겨 만료된
     * 작업이 실행 슬롯을 계속 물고, 뒤이은 워밍·요청이 그만큼 밀린다.
     *
     * <p>그래서 재시도 바깥에 상한을 하나 더 둔다. 429 는 보통 즉시 돌아오므로 정상 경로에서는 이 상한에
     * 닿지 않는다 — 느려졌을 때만 끊는 안전망이다.
     */
    private static final Duration RETRY_TOTAL_TIMEOUT = Duration.ofSeconds(8);
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");

    // 콘텐츠 타입마다 다른 이용시간/휴무일 필드명 후보 (관광지·문화시설·레포츠·음식점).
    private static final String[] USE_TIME_FIELDS = {"usetime", "usetimeculture", "usetimeleports", "opentimefood"};
    private static final String[] REST_DATE_FIELDS = {"restdate", "restdateculture", "restdateleports", "restdatefood"};

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TourApiClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public TourPoiResult findByArea(int areaCode, Integer sigunguCode, Integer contentTypeId, int numOfRows) {
        if (!hasKey()) {
            log.info("TourAPI 키 없음 — 지역기반 조회를 건너뜁니다 (areaCode={})", areaCode);
            return TourPoiResult.empty();
        }
        UriComponentsBuilder builder = base(AREA_BASED)
                // 조회순. 제목순(A)이면 순서가 가나다일 뿐이라 "대표" 와 아무 상관이 없다 — 지역 카드 사진이
                // 공주시는 "가가책방", 부산 동구는 "감포참가자미" 였다. 코스 후보 랭킹도 이 정렬에 기대고 있다.
                .queryParam("arrange", "B")
                .queryParam("areaCode", areaCode)
                .queryParam("numOfRows", numOfRows);
        if (sigunguCode != null) {
            builder.queryParam("sigunguCode", sigunguCode);
        }
        if (contentTypeId != null) {
            builder.queryParam("contentTypeId", contentTypeId);
        }
        return requestList(builder, "areaBased");
    }

    @Override
    public TourPoiResult findByLocation(double lat, double lng, int radiusMeters, Integer contentTypeId, int numOfRows) {
        if (!hasKey()) {
            log.info("TourAPI 키 없음 — 위치기반 조회를 건너뜁니다");
            return TourPoiResult.empty();
        }
        UriComponentsBuilder builder = base(LOCATION_BASED)
                .queryParam("mapX", lng) // TourAPI: mapX=경도, mapY=위도
                .queryParam("mapY", lat)
                .queryParam("radius", radiusMeters)
                .queryParam("arrange", "E") // 거리순
                .queryParam("numOfRows", numOfRows);
        if (contentTypeId != null) {
            builder.queryParam("contentTypeId", contentTypeId);
        }
        return requestList(builder, "locationBased");
    }

    @Override
    public Optional<TourIntro> findIntro(String contentId, int contentTypeId) {
        if (!hasKey()) {
            // 키 없음을 빈 결과로 돌려주면 상세 조회가 "장소 없음(404)"으로 둔갑한다 — 조회 불가(502)로 분리한다.
            throw TourApiException.serviceUnavailable();
        }
        UriComponentsBuilder builder = base(DETAIL_INTRO)
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId);
        try {
            String body = call(builder);
            return parseIntro(body, contentId);
        } catch (Exception e) {
            log.warn("TourAPI 소개정보 조회 실패 cause={}", e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    @Override
    public Optional<TourPoiDetail> findDetail(String contentId) {
        if (!hasKey()) {
            // 키 없음을 빈 결과로 돌려주면 상세 조회가 "장소 없음(404)"으로 둔갑한다 — 조회 불가(502)로 분리한다.
            throw TourApiException.serviceUnavailable();
        }
        UriComponentsBuilder builder = base(DETAIL_COMMON).queryParam("contentId", contentId);
        try {
            return parseDetail(call(builder));
        } catch (Exception e) {
            log.warn("TourAPI 공통상세 조회 실패 cause={}", e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    @Override
    public Optional<TourAccessibility> findAccessibility(String contentId) {
        if (!hasKey()) {
            // 키 없음을 빈 결과로 돌려주면 "등록된 무장애 정보 없음(정상 200)"으로 둔갑한다 — 조회 불가(502)로 분리한다.
            throw TourApiException.serviceUnavailable();
        }
        UriComponentsBuilder builder = base(WITH_BASE, DETAIL_WITH_TOUR).queryParam("contentId", contentId);
        try {
            return parseAccessibility(call(builder), contentId);
        } catch (Exception e) {
            log.warn("TourAPI 무장애정보 조회 실패 cause={}", e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    private boolean hasKey() {
        return props.dataGoKr().hasKey();
    }

    /** 공통 상세(detailCommon2) 와 같은 KorService2 기반 빌더. */
    private UriComponentsBuilder base(String path) {
        return base(BASE, path);
    }

    /** 공통 파라미터를 채운 URI 빌더. serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)). */
    private UriComponentsBuilder base(String baseUrl, String path) {
        return UriComponentsBuilder.fromUriString(baseUrl + path)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json");
    }

    private TourPoiResult requestList(UriComponentsBuilder builder, String op) {
        try {
            return parseList(call(builder));
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("TourAPI 조회 실패 op={} cause={}", op, e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    private String call(UriComponentsBuilder builder) {
        URI uri = builder.build(true).toUri();
        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                // timeout 을 retryWhen 앞에 둔다 — 재시도마다 다시 구독되므로 이 상한은 시도 하나에 걸린다.
                .timeout(TIMEOUT)
                .retryWhen(Retry.backoff(RATE_LIMIT_RETRIES, RATE_LIMIT_BACKOFF)
                        .jitter(RATE_LIMIT_JITTER)
                        .filter(TourApiClientImpl::isRateLimited))
                // 재시도 바깥의 상한 — 시도별 timeout 만으로는 전체가 곱해진다.
                .timeout(RETRY_TOTAL_TIMEOUT)
                .block();
    }

    /** 제공기관이 "지금은 많으니 잠시 뒤" 라고 답한 것인가. 이것만 재시도한다. */
    private static boolean isRateLimited(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    private TourPoiResult parseList(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode bodyNode = response.path("body");
        int totalCount = bodyNode.path("totalCount").asInt(0);

        List<TourPoi> items = new ArrayList<>();
        JsonNode item = bodyNode.path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return new TourPoiResult(items, totalCount);
        }
        for (JsonNode node : item.isArray() ? item : objectMapper.createArrayNode().add(item)) {
            items.add(toPoi(node));
        }
        return new TourPoiResult(items, totalCount);
    }

    private Optional<TourIntro> parseIntro(String body, String contentId) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return Optional.empty();
        }
        JsonNode node = item.isArray() ? (item.isEmpty() ? null : item.get(0)) : item;
        if (node == null) {
            return Optional.empty();
        }
        // 운영시간·휴무일에 <br> 이 줄 구분으로 들어온다(실측) — 매핑 시점에 정제해 화면이 그대로 쓰게 한다(#174).
        return Optional.of(new TourIntro(
                contentId,
                TourText.clean(firstText(node, USE_TIME_FIELDS)),
                TourText.clean(firstText(node, REST_DATE_FIELDS))));
    }

    private Optional<TourPoiDetail> parseDetail(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return Optional.empty();
        }
        JsonNode node = item.isArray() ? (item.isEmpty() ? null : item.get(0)) : item;
        if (node == null) {
            return Optional.empty();
        }
        // 화면에 그대로 나가는 텍스트만 정제한다 — 이미지 URL·좌표는 손대지 않는다(#174).
        return Optional.of(new TourPoiDetail(
                emptyToNull(text(node, "contentid")),
                intOrNull(node, "contenttypeid"),
                TourText.clean(text(node, "title")),
                TourText.clean(text(node, "addr1")),
                TourText.clean(text(node, "tel")),
                doubleOrNull(node, "mapy"),
                doubleOrNull(node, "mapx"),
                emptyToNull(text(node, "firstimage")),
                TourText.clean(text(node, "overview"))));
    }

    private Optional<TourAccessibility> parseAccessibility(String body, String contentId) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return Optional.empty();
        }
        JsonNode node = item.isArray() ? (item.isEmpty() ? null : item.get(0)) : item;
        if (node == null) {
            return Optional.empty();
        }
        return Optional.of(new TourAccessibility(
                contentId,
                text(node, "parking"),
                text(node, "publictransport"),
                text(node, "route"),
                text(node, "ticketoffice"),
                text(node, "promotion"),
                text(node, "wheelchair"),
                text(node, "exit"),
                text(node, "elevator"),
                text(node, "restroom"),
                text(node, "auditorium"),
                text(node, "room"),
                text(node, "handicapetc"),
                text(node, "braileblock"),
                text(node, "helpdog"),
                text(node, "guidehuman"),
                text(node, "audioguide"),
                text(node, "bigprint"),
                text(node, "brailepromotion"),
                text(node, "guidesystem"),
                text(node, "blindhandicapetc"),
                text(node, "signguide"),
                text(node, "videoguide"),
                text(node, "hearingroom"),
                text(node, "hearinghandicapetc"),
                text(node, "stroller"),
                text(node, "lactationroom"),
                text(node, "babysparechair"),
                text(node, "infantsfamilyetc")));
    }

    /**
     * 성공 코드가 아니면 빈결과로 두지 않는다. items 가 없는 실패 응답(키·쿼터·파라미터 오류나 게이트웨이 XML 오류)이 "결과 없음"으로 둔갑하면
     * 추천이 조용히 비어버린다.
     */
    private void requireSuccess(JsonNode response) {
        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODES.contains(resultCode)) {
            throw new IllegalStateException("TourAPI 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }
    }

    private TourPoi toPoi(JsonNode node) {
        return new TourPoi(
                emptyToNull(text(node, "contentid")),
                intOrNull(node, "contenttypeid"),
                emptyToNull(text(node, "lclsSystm1")),
                emptyToNull(text(node, "title")),
                emptyToNull(text(node, "addr1")),
                doubleOrNull(node, "mapy"),
                doubleOrNull(node, "mapx"),
                emptyToNull(text(node, "firstimage")),
                emptyToNull(text(node, "tel")));
    }

    /** JSON 명시적 {@code null}·미존재는 문자열 {@code "null"}/{@code ""} 이 아니라 {@code null} 로 돌려준다(빈값 판정 오염 방지). */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return (value.isNull() || value.isMissingNode()) ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = emptyToNull(text(node, field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        String value = emptyToNull(text(node, field));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        String value = emptyToNull(text(node, field));
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
