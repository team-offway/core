package com.offway.core.trip.infrastructure.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.tour.dto.TourIntro;
import com.offway.core.trip.infrastructure.tour.dto.TourPoi;
import com.offway.core.trip.infrastructure.tour.dto.TourPoiResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final String AREA_BASED = "/areaBasedList2";
    private static final String LOCATION_BASED = "/locationBasedList2";
    private static final String DETAIL_INTRO = "/detailIntro2";

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
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
                .queryParam("arrange", "A") // 제목순
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
            return Optional.empty();
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

    private boolean hasKey() {
        return props.dataGoKr().hasKey();
    }

    /** 공통 파라미터를 채운 URI 빌더. serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)). */
    private UriComponentsBuilder base(String path) {
        return UriComponentsBuilder.fromUriString(BASE + path)
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
                .timeout(TIMEOUT)
                .block();
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
        return Optional.of(new TourIntro(contentId, firstText(node, USE_TIME_FIELDS), firstText(node, REST_DATE_FIELDS)));
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

    private static String text(JsonNode node, String field) {
        return node.path(field).asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = emptyToNull(node.path(field).asText());
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
