package com.offway.core.transport.infrastructure.tmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.transport.domain.UnroutableReason;
import com.offway.core.transport.infrastructure.tmap.dto.CarRouteResult;
import com.offway.core.transport.infrastructure.tmap.dto.TmapRoute;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.external.ExternalKeyState;
import com.offway.core.common.external.FallbackKeyAlert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * TMAP 자동차 경로 adapter (SK 오픈API {@code /tmap/routes}). 인증은 {@code appKey} 헤더.
 *
 * <p>키가 없으면 외부 호출 없이 빈 결과(로컬 실행성). 호출·파싱 실패도 빈 결과로 돌려 상위(코스)가 직선거리로 폴백하게 한다 —
 * 이동시간은 보조 정보라 실패로 코스 전체를 막지 않는다. 좌표는 TMAP 규약대로 X=경도·Y=위도(WGS84GEO).
 */
@Slf4j
@Component
class TmapClientImpl implements TmapClient {

    private static final String ROUTES_URL = "https://apis.openapi.sk.com/tmap/routes?version=1";
    private static final String OPTIMIZE_URL = "https://apis.openapi.sk.com/tmap/routes/routeOptimization10";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int SECONDS_PER_MINUTE = 60;
    private static final double METERS_PER_KM = 1000.0;
    /** routeOptimization10: 출발+경유(≥1)+도착 = 최소 3, 경유지 최대 10곳(총 12). */
    private static final int MIN_OPTIMIZE_POINTS = 3;
    private static final int MAX_OPTIMIZE_POINTS = 12;
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    /** 보조 키가 응답은 줬는데 쓸 수 없을 때의 알림 사유 — 응답 원문은 싣지 않는다. */
    private static final String FALLBACK_UNUSABLE = "보조 키 응답 해석 실패";

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final FallbackKeyAlert fallbackKeyAlert;
    private final ExternalKeyState keyState;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TmapClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder, FallbackKeyAlert fallbackKeyAlert,
            ExternalKeyState keyState) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
        this.fallbackKeyAlert = fallbackKeyAlert;
        this.keyState = keyState;
    }

    /**
     * 오늘 먼저 쓸 키 — 주 키가 마른 것이 <b>확인된</b> 날이면 보조 키부터 간다(#596).
     *
     * <p>이 선택이 없으면 마른 뒤에도 요청마다 죽은 키를 한 번씩 두드린다. 호출도 지연도 두 배가 되고,
     * 주 키 집계는 100% 를 넘어 계속 오른다 — 이미 아는 사실을 매번 다시 확인하는 셈이다.
     */
    private String firstKey(ExternalApi api) {
        return keyState.usingFallback(api) && props.tmap().hasFallback()
                ? props.tmap().fallbackKey()
                : props.tmap().appKey();
    }

    /** 먼저 쓴 키가 실패했을 때 넘어갈 쪽 — 주/보조를 서로 맞바꾼다. */
    private String otherKey(ExternalApi api) {
        return keyState.usingFallback(api) && props.tmap().hasFallback()
                ? props.tmap().appKey()
                : props.tmap().fallbackKey();
    }

    /**
     * TMAP 에 한 번 던진다 — 키는 <b>헤더</b>로 간다(URL 에 안 실린다).
     *
     * @param appKey 주 키이거나 보조 키
     */
    /**
     * 주 키로 나가는 호출만 센다(#596).
     *
     * <p>보조 키로 도는 날에는 <b>첫 호출도 보조 키다.</b> 그것까지 세면 주 키 사용량이 실제보다
     * 커지고, 정작 그 숫자는 그날 더 움직이지 않아야 맞다.
     */
    private void recordIfPrimary(ExternalApi api) {
        if (!keyState.usingFallback(api)) {
            callRecorder.record(api);
        }
    }

    private String post(String url, String body, String appKey) {
        return webClient.post()
                .uri(url)
                .header("appKey", appKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
    }

    /**
     * 주 키가 실패했을 때 <b>보조 키로 한 번만</b> 더 간다(#596).
     *
     * <h2>사유를 가리지 않는다</h2>
     *
     * <p>한도인지 일시 오류인지 판정해서 결정하면, 그 판정이 틀렸을 때 <b>멀쩡한 키를 버리거나 마른 키를
     * 붙든다.</b> 실패했다는 사실만 보고 한 번 더 간다 — 정상일 때는 이 경로를 안 타므로 호출이 늘지 않는다.
     *
     * <h2>TMAP 은 "오늘은 보조 키" 를 기억하지 않는다</h2>
     *
     * <p><b>한도 소진을 단정할 신호가 없다.</b> 429 가 오지만 그건 일일 한도일 수도, 초당 유량 제한일
     * 수도 있다 — TourAPI 쪽은 같은 429 를 "잠시 뒤 재시도" 로 다룬다. 그걸 한도로 읽고 기억을 달면
     * <b>일시적인 제한 한 번이 그날 내내 보조 키를 쓰게 만들고</b>, 정작 진짜로 마르는 순간에 보조 키
     * 한도가 이미 닳아 있다.
     *
     * <p>그래서 TMAP 은 매번 주 키부터 간다. 마른 날에는 요청마다 실패한 호출이 하나 더 나가지만,
     * 그 대가가 잘못 붙든 기억보다 싸다. 신호를 확인하면 그때 {@code keyState} 에 기억을 붙인다
     * (data.go.kr 은 {@code reasonCode=22} 로 단정할 수 있어 이미 그렇게 한다).
     *
     * <h2>보조 키 호출은 한도 집계에 넣지 않는다</h2>
     *
     * <p>넣으면 주 키 사용량이 부풀어 보인다. 그 집계는 "우리가 이 API 를 얼마나 쓰나" 를 말하는
     * 자료이고 #402 심사 자료로도 나가므로, 다른 키로 나간 호출을 거기 섞지 않는다. 대신 알림으로 남긴다.
     *
     * <h2>해석까지 끝나야 "받아 줬다" 로 본다</h2>
     *
     * <p>응답이 왔다는 것만으로 전환 알림("화면은 정상입니다")을 보내면, 그 응답을 못 쓰는 경우에
     * 알림이 거짓말을 한다 — 사용자는 폴백 결과를 보는데 운영 채널에는 정상이라고 남는다. 그래서 파서를
     * 받아 <b>쓸 수 있는 결과가 나온 뒤에</b> 알린다.
     *
     * @param parser 보조 키 응답 해석. 던지지 않고 빈 값으로 실패를 알린다
     * @return 보조 키 응답을 해석한 결과. 보조 키가 없거나, 실패하거나, 해석이 안 되면 빈 값
     */
    private <T> Optional<T> retryWithFallback(String url, String body, ExternalApi api, Exception primaryFailure,
            Function<String, Optional<T>> parser) {
        String cause = RootCause.label(primaryFailure);
        String next = otherKey(api);
        if (next == null || next.isBlank()) {
            fallbackKeyAlert.noFallbackConfigured(api, cause);
            return Optional.empty();
        }
        String response;
        try {
            response = post(url, body, next);
        } catch (Exception e) {
            fallbackKeyAlert.bothFailed(api, RootCause.label(e));
            log.warn("TMAP 보조 키도 실패했습니다 api={} cause={}", api, RootCause.of(e));
            return Optional.empty();
        }
        Optional<T> parsed = response == null ? Optional.empty() : parser.apply(response);
        if (parsed.isEmpty()) {
            fallbackKeyAlert.bothFailed(api, FALLBACK_UNUSABLE);
            return Optional.empty();
        }
        fallbackKeyAlert.switchedToFallback(api, cause);
        return parsed;
    }

    @Override
    public CarRouteResult carRoute(Coordinate origin, Coordinate destination) {
        if (!props.tmap().hasKey()) {
            return CarRouteResult.Unavailable.instance();
        }
        String body;
        try {
            body = requestBody(origin, destination);
        } catch (Exception e) {
            log.warn("TMAP 경로 요청 본문을 만들지 못했습니다 cause={}", RootCause.of(e));
            return CarRouteResult.Unavailable.instance();
        }
        try {
            // 경로 탐색과 경유지 최적화는 한도가 다르다(1,000 vs 50). 같은 클라이언트지만 따로 센다.
            recordIfPrimary(ExternalApi.TMAP_ROUTE);
            return parse(post(ROUTES_URL, body, firstKey(ExternalApi.TMAP_ROUTE)))
                    .<CarRouteResult>map(CarRouteResult.Found::new)
                    .orElseGet(CarRouteResult.Unavailable::instance);
        } catch (Exception e) {
            // 실패는 폴백으로 흡수하되 **사유는 남긴다**. TMAP 은 거절 이유를 응답 본문의 code 로 주는데
            // (1100 도로 링크 없음 · 1009 한반도 범위 초과) 예외 클래스명은 둘 다 BadRequest 라 못 가른다.
            // 그 한 줄이 없어 원인을 찾는 데 실호출 210건이 들었다(#334). RootCause 가 키·URL 은 가린다.
            log.warn("TMAP 경로 조회 실패 cause={}", RootCause.of(e));
            // 그 code 를 로그로만 흘리지 않고 판정에 쓴다(#335). 좌표 탓이면 상위가 기억해 다음 코스에서 뺀다.
            Optional<UnroutableReason> rejected = rejectionOf(e);
            if (rejected.isPresent()) {
                // **좌표 탓이면 보조 키로 다시 묻지 않는다**(#596). 도로 링크가 없는 지점은 어느 키로
                // 물어도 없다 — 재시도해 봐야 보조 키 한도만 태우고 답은 같다.
                return new CarRouteResult.Rejected(rejected.get());
            }
            return retryWithFallback(ROUTES_URL, body, ExternalApi.TMAP_ROUTE, e, this::parseQuietly)
                    .<CarRouteResult>map(CarRouteResult.Found::new)
                    .orElseGet(CarRouteResult.Unavailable::instance);
        }
    }

    /** 보조 키 응답 파싱 — 여기서 또 던지면 폴백을 넣은 의미가 없다. */
    private Optional<TmapRoute> parseQuietly(String body) {
        try {
            return parse(body);
        } catch (Exception e) {
            log.warn("TMAP 보조 키 응답을 해석하지 못했습니다 cause={}", RootCause.of(e));
            return Optional.empty();
        }
    }

    /**
     * 응답 본문의 {@code code} 가 <b>좌표 탓</b>인 사유인지 본다.
     *
     * <p>본문이 없거나(타임아웃·연결 실패) 모르는 code 면 빈 값이다 — 그때는 일시적 실패로 다뤄 아무것도
     * 기억하지 않는다. <b>모르는 것을 좌표 탓으로 몰면 멀쩡한 장소가 영구히 사라진다.</b>
     *
     * <p>본문 모양이 바뀔 수 있어 {@code error.code} 와 최상위 {@code code} 를 둘 다 본다. 문자열로도
     * 숫자로도 오므로 {@code asText} 로 읽는다.
     */
    private Optional<UnroutableReason> rejectionOf(Throwable error) {
        return responseBodyOf(error).flatMap(body -> {
            try {
                JsonNode root = objectMapper.readTree(body);
                JsonNode code = root.path("error").path("code");
                if (code.isMissingNode()) {
                    code = root.path("code");
                }
                return UnroutableReason.fromTmapCode(code.asText(null));
            } catch (Exception parseFailure) {
                return Optional.empty();
            }
        });
    }

    /** 체인 전체를 뒤진다 — WebClient 예외는 Reactive 예외로 감싸여 오므로 맨 끝만 보면 껍데기를 만난다. */
    private static Optional<String> responseBodyOf(Throwable error) {
        for (Throwable cause = error; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof WebClientResponseException response
                    && !response.getResponseBodyAsString().isBlank()) {
                return Optional.of(response.getResponseBodyAsString());
            }
        }
        return Optional.empty();
    }

    private String requestBody(Coordinate origin, Coordinate destination) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "startX", origin.lng(), "startY", origin.lat(),
                "endX", destination.lng(), "endY", destination.lat(),
                "reqCoordType", "WGS84GEO", "resCoordType", "WGS84GEO",
                "searchOption", "0", "startName", "출발", "endName", "도착"));
    }

    /** TMAP GeoJSON 의 첫 feature properties 에 요약(totalTime 초·totalDistance m)이 온다. */
    private Optional<TmapRoute> parse(String body) throws Exception {
        JsonNode features = objectMapper.readTree(body).path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        JsonNode properties = features.get(0).path("properties");
        int totalTime = properties.path("totalTime").asInt(0);
        int totalDistance = properties.path("totalDistance").asInt(0);
        if (totalTime <= 0) {
            return Optional.empty();
        }
        int minutes = Math.max(1, Math.round((float) totalTime / SECONDS_PER_MINUTE));
        return Optional.of(new TmapRoute(minutes, totalDistance / METERS_PER_KM));
    }

    @Override
    public Optional<List<Integer>> optimizeCarOrder(List<Coordinate> points) {
        if (!props.tmap().hasKey() || points.size() < MIN_OPTIMIZE_POINTS || points.size() > MAX_OPTIMIZE_POINTS) {
            return Optional.empty();
        }
        String body;
        try {
            body = optimizeBody(points);
        } catch (Exception e) {
            log.warn("TMAP 경유지 요청 본문을 만들지 못했습니다 cause={}", RootCause.of(e));
            return Optional.empty();
        }
        try {
            // 우리가 가진 것 중 가장 빡빡한 한도(50/일). #110 에서 80% 소진 알림을 실제로 받았다.
            recordIfPrimary(ExternalApi.TMAP_WAYPOINT);
            return parseOrder(post(OPTIMIZE_URL, body, firstKey(ExternalApi.TMAP_WAYPOINT)), points.size());
        } catch (Exception e) {
            // **여기가 보조 키를 만든 이유다**(#596). 한도 50 이라 자차 코스 17건이면 마르는데, 마르면
            // 위 호출이 실패하고 상위가 직선거리 정렬로 떨어진다 — 그런데 응답은 200 이라 순서가 틀린 줄
            // 화면에서 알 수 없다. 심사처럼 다시 할 수 없는 자리에서는 그 조용함이 곧 결과가 된다.
            log.warn("TMAP 경유지 최적화 실패 cause={}", RootCause.of(e));
            // **좌표 탓이면 보조 키로 다시 묻지 않는다** — carRoute 와 같은 규칙이다. 어느 키로 물어도 답이
            // 같고, 이쪽은 한도가 50 이라 한 번이 더 비싸다. 상위가 직선거리 정렬로 떨어진다.
            if (rejectionOf(e).isPresent()) {
                return Optional.empty();
            }
            return retryWithFallback(OPTIMIZE_URL, body, ExternalApi.TMAP_WAYPOINT, e,
                    response -> parseOrderQuietly(response, points.size()));
        }
    }

    /** 보조 키 응답 파싱 — 여기서 또 던지면 폴백을 넣은 의미가 없다. */
    private Optional<List<Integer>> parseOrderQuietly(String response, int size) {
        try {
            return parseOrder(response, size);
        } catch (Exception e) {
            log.warn("TMAP 보조 키 경유지 응답을 해석하지 못했습니다 cause={}", RootCause.of(e));
            return Optional.empty();
        }
    }

    private String optimizeBody(List<Coordinate> points) throws Exception {
        Coordinate start = points.get(0);
        Coordinate end = points.get(points.size() - 1);
        List<Map<String, Object>> vias = new ArrayList<>();
        for (int i = 1; i < points.size() - 1; i++) {
            Coordinate via = points.get(i);
            vias.add(Map.of(
                    "viaPointId", String.valueOf(i), "viaPointName", "v" + i,
                    "viaX", String.valueOf(via.lng()), "viaY", String.valueOf(via.lat())));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        body.put("carType", "0");
        body.put("startName", "출발");
        body.put("startX", String.valueOf(start.lng()));
        body.put("startY", String.valueOf(start.lat()));
        body.put("startTime", LocalDateTime.now().format(START_TIME));
        body.put("endName", "도착");
        body.put("endX", String.valueOf(end.lng()));
        body.put("endY", String.valueOf(end.lat()));
        body.put("viaPoints", vias);
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 응답의 Point feature 로 최적 순서를 복원한다. pointType 이 {@code S}(출발)·{@code B{n}}(경유 n번째)·{@code E}(도착)
     * 이고, 경유 지점은 {@code viaPointId}(우리가 심은 입력 인덱스)로 원 위치를 안다. 예상 개수와 다르면 폴백을 위해 빈 결과.
     */
    private Optional<List<Integer>> parseOrder(String body, int size) throws Exception {
        JsonNode features = objectMapper.readTree(body).path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        List<int[]> vias = new ArrayList<>(); // [순서n, 원본인덱스]
        for (JsonNode feature : features) {
            if (!"Point".equals(feature.path("geometry").path("type").asText())) {
                continue;
            }
            String pointType = feature.path("properties").path("pointType").asText("");
            if (pointType.startsWith("B")) {
                int sequence = Integer.parseInt(pointType.substring(1));
                int original = Integer.parseInt(feature.path("properties").path("viaPointId").asText());
                vias.add(new int[] {sequence, original});
            }
        }
        if (vias.size() != size - 2) {
            return Optional.empty();
        }
        if (!isPermutation(vias, size)) {
            log.warn("TMAP 경유지 순서가 자리바꿈이 아닙니다 — 직선거리 정렬로 폴백 점개수={}", size);
            return Optional.empty();
        }
        vias.sort(Comparator.comparingInt(via -> via[0]));
        List<Integer> order = new ArrayList<>();
        order.add(0);
        vias.forEach(via -> order.add(via[1]));
        order.add(size - 1);
        return Optional.of(order);
    }

    /**
     * 돌려받은 경유지들이 <b>우리가 보낸 것의 자리바꿈</b>인가 — 각 원본 인덱스가 {@code 1..size-2} 에서
     * 정확히 한 번씩.
     *
     * <p>개수만 세면 부족하다. {@code viaPointId} 는 <b>외부가 채워 주는 값</b>이라 같은 인덱스가 두 번
     * 오거나 범위를 벗어나도 개수는 맞을 수 있고, 그러면 <b>한 장소가 코스에 두 번 들어가고 다른
     * 하나는 사라진다.</b> 범위를 벗어나면 그 인덱스를 쓰는 쪽에서 터진다.
     *
     * <p>여기서 끊는 이유: 이 값은 이제 <b>캐시에 최대 30일 남는다</b>(#584). 한 번 잘못 받으면 그동안
     * 같은 구간이 계속 틀린 순서로 나가고, TMAP 을 다시 안 부르니 저절로 낫지도 않는다. 폴백은
     * 캐시하지 않으므로 빈 결과로 돌리면 다음 요청이 다시 묻는다.
     */
    private static boolean isPermutation(List<int[]> vias, int size) {
        boolean[] seen = new boolean[size];
        for (int[] via : vias) {
            int original = via[1];
            if (original <= 0 || original >= size - 1 || seen[original]) {
                return false;
            }
            seen[original] = true;
        }
        return true;
    }
}
