package com.offway.core.trip.infrastructure.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.dto.TourAccessibility;
import com.offway.core.trip.infrastructure.tour.dto.TourFestival;
import com.offway.core.trip.infrastructure.tour.dto.TourFestivalResult;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiDetail;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.DataGoKrError;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalKeyState;
import com.offway.core.common.external.FallbackKeyAlert;
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
    private static final String DETAIL_IMAGE = "/detailImage2";

    /**
     * 한 장소에서 받아 올 사진 수 상한 — 실측으로 완도타워가 16장이다.
     *
     * <p>화면이 한 장소에 그만큼 다 쓰지 않고, 응답이 커지면 {@code maxInMemorySize} 와 직렬화 비용이
     * 함께 올라간다. 갤러리로 쓰기에 충분한 선에서 자른다.
     */
    private static final int MAX_IMAGES = 10;
    private static final String DETAIL_WITH_TOUR = "/detailWithTour2";

    /** 행사정보(#388) — 축제 기간을 주는 유일한 오퍼레이션. areaBasedList2 는 날짜를 안 준다. */
    private static final String SEARCH_FESTIVAL = "/searchFestival2";

    /** TourAPI 가 받는 날짜 형식 — {@code 20260912} 처럼 구분자가 없다. */
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

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

    /**
     * 재시도분을 셀 때 건너뛰는 시도 번호 — 0번째(최초 호출)는 실호출 직전에 이미 셌다(#365).
     *
     * <p>이 값이 0 이 되면 평범한 호출이 두 번 세어져, 고치려던 것과 정반대로 어긋난다.
     */
    private static final int FIRST_RETRY_ATTEMPT = 1;

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");
    /** 보조 키가 응답은 줬는데 성공 응답이 아닐 때의 알림 사유 — 응답 원문은 싣지 않는다. */
    private static final String FALLBACK_NOT_SUCCESS = "보조 키 응답이 성공이 아님";

    // 콘텐츠 타입마다 다른 이용시간/휴무일 필드명 후보 (관광지·문화시설·레포츠·음식점).
    private static final String[] USE_TIME_FIELDS = {"usetime", "usetimeculture", "usetimeleports", "opentimefood"};
    private static final String[] REST_DATE_FIELDS = {"restdate", "restdateculture", "restdateleports", "restdatefood"};

    // 카테고리마다 이름이 다르지만 뜻이 같은 것들. 한 카테고리의 응답에는 그중 하나만 들어 있어,
    // 먼저 잡히는 값을 쓰면 된다(#157).
    private static final String[] PARKING_FIELDS = {"parking", "parkingculture", "parkingleports", "parkingfood"};
    private static final String[] FEE_FIELDS = {"usefee", "usefeeleports"};
    private static final String[] SIGNATURE_MENU_FIELDS = {"firstmenu"};
    private static final String[] MENU_FIELDS = {"treatmenu"};
    private static final String[] CHECK_IN_FIELDS = {"checkintime"};
    private static final String[] CHECK_OUT_FIELDS = {"checkouttime"};
    private static final String[] ROOM_COUNT_FIELDS = {"roomcount"};
    private static final String[] RESERVATION_FIELDS = {"reservationlodging", "reservationfood"};

    /** 체험안내 — 체험 카드의 부제(#305). 요금은 체험 응답에 아예 없어 이 칸이 실질 재료다. */
    private static final String[] EXPERIENCE_GUIDE_FIELDS = {"expguide"};

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final FallbackKeyAlert fallbackKeyAlert;
    private final ExternalKeyState keyState;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TourApiClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder, FallbackKeyAlert fallbackKeyAlert,
            ExternalKeyState keyState) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
        this.fallbackKeyAlert = fallbackKeyAlert;
        this.keyState = keyState;
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
    public TourFestivalResult findFestivals(LocalDate from, int pageNo, int numOfRows) {
        if (!hasKey()) {
            log.info("TourAPI 키 없음 — 축제 기간 조회를 건너뜁니다");
            return TourFestivalResult.empty();
        }
        UriComponentsBuilder builder = base(SEARCH_FESTIVAL)
                // 시작일 오름차순. 페이지를 도는 중에 순서가 흔들리면 같은 축제를 두 번 받거나 빠뜨린다.
                .queryParam("arrange", "A")
                .queryParam("eventStartDate", API_DATE.format(from))
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows);
        try {
            return parseFestivals(call(builder));
        } catch (Exception e) {
            log.warn("TourAPI 축제 조회 실패 pageNo={} cause={}", pageNo, RootCause.of(e));
            throw TourApiException.lookupFailed(e);
        }
    }

    /**
     * 축제 응답을 기간으로 옮긴다 — <b>날짜가 깨진 줄은 버리고 나머지는 살린다</b>.
     *
     * <p>한 줄 때문에 페이지 전체를 날리지 않는다. 버려진 줄은 저장되지 않아 그 축제가 지금처럼 평범한
     * 볼거리로 남을 뿐이고, 그건 "모르는 것을 끝났다고 단정" 하는 것보다 낫다.
     */
    private TourFestivalResult parseFestivals(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode bodyNode = response.path("body");
        int totalCount = bodyNode.path("totalCount").asInt(0);

        List<TourFestival> items = new ArrayList<>();
        JsonNode item = bodyNode.path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return new TourFestivalResult(items, totalCount);
        }
        for (JsonNode node : item.isArray() ? item : objectMapper.createArrayNode().add(item)) {
            TourFestival.of(
                            text(node, "contentid"),
                            text(node, "title"),
                            text(node, "eventstartdate"),
                            text(node, "eventenddate"))
                    .ifPresent(items::add);
        }
        return new TourFestivalResult(items, totalCount);
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
            log.warn("TourAPI 소개정보 조회 실패 cause={}", RootCause.of(e));
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
            log.warn("TourAPI 공통상세 조회 실패 cause={}", RootCause.of(e));
            throw TourApiException.lookupFailed(e);
        }
    }

    @Override
    public List<String> findImages(String contentId) {
        if (!hasKey()) {
            // **던지지 않는다.** 사진은 상세의 곁가지라, 키가 없다고 장소 상세 전체가 502 가 되면 안 된다.
            log.info("TourAPI 키 없음 — 장소 사진 조회를 건너뜁니다");
            return List.of();
        }
        UriComponentsBuilder builder = base(DETAIL_IMAGE)
                .queryParam("contentId", contentId)
                .queryParam("imageYN", "Y")
                .queryParam("numOfRows", MAX_IMAGES);
        try {
            return parseImages(call(builder));
        } catch (Exception e) {
            // 같은 이유로 삼킨다. 사진 한 장 때문에 상세가 통째로 실패하는 쪽이 훨씬 나쁘다.
            log.warn("TourAPI 장소 사진 조회 실패 cause={}", RootCause.of(e));
            return List.of();
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
            log.warn("TourAPI 무장애정보 조회 실패 cause={}", RootCause.of(e));
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
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다 — RootCause 가 마스킹·제어문자 제거·길이 제한을 건다.
            log.warn("TourAPI 조회 실패 op={} cause={}", op, RootCause.of(e));
            throw TourApiException.lookupFailed(e);
        }
    }

    /**
     * 조회 한 번 — 주 키가 <b>한도로 막히면 보조 키로 한 번만</b> 더 간다(#596).
     *
     * <h2>한도 소진은 예외로 오지 않는다</h2>
     *
     * <p>게이트웨이가 <b>HTTP 200</b> 에 거절 envelope 을 실어 준다. 그래서 아래 fetch 는 성공으로
     * 끝나고, 실패는 한참 뒤 파싱 자리에서 난다 — 그 자리에서는 "한도" 인지 "응답 모양이 바뀐 것" 인지
     * 못 가른다. 본문을 받은 <b>바로 여기서</b> 판정한다.
     *
     * <p><b>보조 키 호출은 한도 집계에 넣지 않는다.</b> 다른 키로 나간 것을 주 키 사용량에 섞으면
     * 그 숫자가 틀린다 — #402 심사 자료가 그 집계를 쓴다.
     *
     * <h2>다른 키로 넘어간 호출은 조용히 끝나면 안 된다</h2>
     *
     * <p>주 키를 "오늘은 마름" 으로 기억한 뒤라, 보조 키 쪽 실패를 알리지 않으면 <b>그날 남은 요청이
     * 전부 알림 없이 502</b> 가 된다 — 이 폴백이 막으려던 조용한 실패 그대로다. 그래서 예외로 끝나도,
     * 게이트웨이가 다른 사유(미등록 키 등)로 거절해도 "둘 다 실패" 로 알린다.
     */
    private String call(UriComponentsBuilder builder) {
        boolean onFallback = keyState.usingFallback(ExternalApi.TOUR_API) && props.dataGoKr().hasFallback();
        String firstKey = onFallback ? props.dataGoKr().fallbackKey() : props.dataGoKr().serviceKey();
        // 주 키로 나가는 호출만 센다 — 보조 키로 도는 날 첫 호출까지 세면 그 숫자가 실제와 어긋난다.
        String body = onFallback
                ? alertingFailure(() -> fetch(withKey(builder, firstKey), false, true))
                : fetch(withKey(builder, firstKey), true, true);
        if (!DataGoKrError.isQuotaExceeded(body)) {
            // 보조 키로 도는 날에는 첫 호출도 "다른 키로 나간 호출" 이다. 성공이 아닌 응답(미등록 키·resultCode
            // 실패)을 그대로 돌려보내면 파서가 502 로 끝내는데 알림은 없다 — 그날 내내 조용히 실패한다.
            if (onFallback && !isSuccessResponse(body)) {
                fallbackKeyAlert.bothFailed(ExternalApi.TOUR_API, FALLBACK_NOT_SUCCESS);
            }
            return body;
        }
        // 게이트웨이가 한도라고 말했다 — 오늘은 이쪽을 먼저 안 쓴다.
        if (!onFallback) {
            keyState.markPrimaryExhausted(ExternalApi.TOUR_API);
        }
        String otherKey = onFallback ? props.dataGoKr().serviceKey() : props.dataGoKr().fallbackKey();
        if (otherKey == null || otherKey.isBlank()) {
            fallbackKeyAlert.noFallbackConfigured(ExternalApi.TOUR_API, "한도 소진");
            return body; // 그대로 올려보낸다 — 파싱이 실패로 판정하고 502 로 나간다
        }
        log.warn("TourAPI 한도가 말라 다른 키로 넘어갑니다");
        // **다른 키로는 한 번만 간다** — 429 재시도를 끈다. 켜 두면 보조 키로 최대 세 번이 나가
        // "한 번만 더" 라는 약속이 깨지고, 그만큼 보조 키 한도를 태운다.
        String retried = alertingFailure(() -> fetch(withKey(builder, otherKey), onFallback, false));
        if (DataGoKrError.isQuotaExceeded(retried)) {
            fallbackKeyAlert.bothFailed(ExternalApi.TOUR_API, "한도 소진");
            return retried;
        }
        // 한도가 아니어도 성공 응답이 아니면(미등록 키·만료·resultCode 실패) 받아 준 것이 아니다. 여기서
        // "보조 키로 넘어갔습니다. 화면은 정상입니다" 를 보내면, 뒤의 파서가 502 로 끝내는데 알림은 정상이라고
        // 남는다. 판정 기준은 파서들이 쓰는 requireSuccess 와 같다 — 둘이 다르면 그 틈으로 거짓 알림이 샌다.
        if (!isSuccessResponse(retried)) {
            fallbackKeyAlert.bothFailed(ExternalApi.TOUR_API, FALLBACK_NOT_SUCCESS);
            return retried;
        }
        fallbackKeyAlert.switchedToFallback(ExternalApi.TOUR_API, "한도 소진");
        return retried;
    }

    /** 파서의 {@link #requireSuccess} 와 같은 기준으로 본다 — 게이트웨이 거절 envelope 은 {@code response} 가 없어 여기서 걸린다. */
    private boolean isSuccessResponse(String body) {
        try {
            String resultCode = objectMapper.readTree(body).path("response").path("header").path("resultCode").asText();
            return SUCCESS_CODES.contains(resultCode);
        } catch (Exception e) {
            return false;
        }
    }

    /** 다른 키로 나간 호출이 예외로 끝나면 알리고 그대로 던진다 — 삼키면 502 가 알림 없이 이어진다. */
    private String alertingFailure(Supplier<String> fetch) {
        try {
            return fetch.get();
        } catch (RuntimeException e) {
            fallbackKeyAlert.bothFailed(ExternalApi.TOUR_API, RootCause.label(e));
            throw e;
        }
    }

    /** serviceKey 만 갈아 끼운다 — 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)). */
    private static URI withKey(UriComponentsBuilder builder, String serviceKey) {
        return builder.replaceQueryParam("serviceKey", serviceKey).build(true).toUri();
    }

    /**
     * @param count 한도 집계에 넣을 것인가. 보조 키로 나가는 호출은 <b>넣지 않는다</b> — 주 키 사용량이
     *     부풀어 보이면 "얼마나 쓰나" 를 말하는 그 숫자가 틀린다
     * @param retryRateLimited 429 를 재시도할 것인가. 주 키가 마른 뒤 <b>다른 키로 한 번 더</b> 가는 호출은
     *     끈다 — 그 호출은 한 번이라는 것이 계약이다
     */
    private String fetch(URI uri, boolean count, boolean retryRateLimited) {
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        if (count) {
            callRecorder.record(ExternalApi.TOUR_API);
        }
        AtomicInteger attempts = new AtomicInteger();
        try {
            return webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    // 구독마다 요청이 새로 나간다. retryWhen 이 다시 구독하므로 여기서 세면 재시도가 잡힌다.
                    .doOnSubscribe(subscription -> attempts.incrementAndGet())
                    // timeout 을 retryWhen 앞에 둔다 — 재시도마다 다시 구독되므로 이 상한은 시도 하나에 걸린다.
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.backoff(retryRateLimited ? RATE_LIMIT_RETRIES : 0, RATE_LIMIT_BACKOFF)
                            .jitter(RATE_LIMIT_JITTER)
                            .filter(TourApiClientImpl::isRateLimited))
                    // 재시도 바깥의 상한 — 시도별 timeout 만으로는 전체가 곱해진다.
                    .timeout(RETRY_TOTAL_TIMEOUT)
                    .block();
        } finally {
            // 실패로 끝나도 센다 — 나간 호출은 이미 한도를 깎았다. 보조 키로 나간 것은 세지 않는다(#596).
            if (count) {
                recordRetries(attempts.get());
            }
        }
    }

    /**
     * 재시도로 <b>더</b> 나간 호출을 마저 센다(#365). 최초 1회는 위에서 이미 셌으므로 그만큼을 뺀다.
     *
     * <p><b>왜 세는 자리와 적는 자리를 갈랐나.</b> 재시도는 Reactor 의 backoff 스케줄러 스레드에서 돈다.
     * 거기서 바로 적으면 둘이 어긋난다 — 기록은 DB 와 디스코드를 타는 blocking 작업이라 스케줄러 스레드를
     * 붙잡고, 호출 주체는 스레드 지역이라({@code CallerContext}) 그 스레드에서는 UNKNOWN 으로 잡힌다.
     * 재시도가 전부 미상으로 들어가면 #285 가 하려던 "누가 태웠나" 가 그만큼 흐려진다.
     *
     * <p>그래서 스케줄러 스레드에서는 <b>세기만</b> 하고, 부른 스레드로 돌아와 적는다.
     */
    private void recordRetries(int attempts) {
        for (int retried = FIRST_RETRY_ATTEMPT; retried < attempts; retried++) {
            callRecorder.record(ExternalApi.TOUR_API);
        }
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
        return Optional.of(TourIntro.builder()
                .contentId(contentId)
                .useTime(TourText.clean(firstText(node, USE_TIME_FIELDS)))
                .restDate(TourText.clean(firstText(node, REST_DATE_FIELDS)))
                .parking(TourText.clean(firstText(node, PARKING_FIELDS)))
                .fee(TourText.clean(firstText(node, FEE_FIELDS)))
                .signatureMenu(TourText.clean(firstText(node, SIGNATURE_MENU_FIELDS)))
                .menus(TourText.clean(firstText(node, MENU_FIELDS)))
                .checkIn(TourText.clean(firstText(node, CHECK_IN_FIELDS)))
                .checkOut(TourText.clean(firstText(node, CHECK_OUT_FIELDS)))
                .roomCount(TourText.clean(firstText(node, ROOM_COUNT_FIELDS)))
                .reservation(TourText.clean(firstText(node, RESERVATION_FIELDS)))
                .experienceGuide(TourText.clean(firstText(node, EXPERIENCE_GUIDE_FIELDS)))
                .build());
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

    /**
     * 사진 목록 — 원본 크기({@code originimgurl})를 쓴다.
     *
     * <p>썸네일({@code smallimageurl})도 오지만 화면이 크게 쓰므로 원본이 맞다. 둘 다 없는 항목은 버린다.
     */
    private List<String> parseImages(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        requireSuccess(response);

        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode node : item.isArray() ? item : objectMapper.createArrayNode().add(item)) {
            String url = emptyToNull(text(node, "originimgurl"));
            if (url != null && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return List.copyOf(urls);
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
        // **빌더로 짠다.** 열한 칸을 위치로 넘기면 필드가 늘 때 순서가 어긋나도 컴파일이 통과한다 —
        // 좌표 두 칸(mapy·mapx)이 서로 바뀌어도 그렇다.
        return TourPoi.builder()
                .contentId(emptyToNull(text(node, "contentid")))
                .contentTypeId(intOrNull(node, "contenttypeid"))
                .lclsSystm1(emptyToNull(text(node, "lclsSystm1")))
                .title(emptyToNull(text(node, "title")))
                .address(emptyToNull(text(node, "addr1")))
                .lat(doubleOrNull(node, "mapy"))
                .lng(doubleOrNull(node, "mapx"))
                .firstImage(emptyToNull(text(node, "firstimage")))
                .tel(emptyToNull(text(node, "tel")))
                .lclsSystm2(emptyToNull(text(node, "lclsSystm2")))
                .cat3(emptyToNull(text(node, "cat3")))
                .build();
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
