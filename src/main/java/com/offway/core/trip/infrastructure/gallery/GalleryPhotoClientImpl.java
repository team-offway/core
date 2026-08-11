package com.offway.core.trip.infrastructure.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.gallery.dto.GalleryPhotoItem;
import java.net.URI;
import java.time.Duration;
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
 * 관광사진 갤러리 adapter — {@code PhotoGalleryService1/galleryList1}(#196).
 *
 * <p>실측(2026-08-09): 전량 <b>6,118건</b>. {@code numOfRows=1000} 이 그대로 받아들여져 <b>7페이지</b>면
 * 끝나고, 페이지당 554KB·0.35초다. 페이지 크기를 키운 이유는 호출 수를 줄이려는 것인데, 응답이 커지므로
 * {@code externalWebClient} 의 {@code maxInMemorySize}(2MB) 안에 드는지 확인하고 정했다.
 *
 * <p>키가 없으면 외부 호출 없이 빈 목록(로컬 실행성). 호출·파싱 실패는 {@link TourApiException}(502)으로 올린다.
 */
@Slf4j
@Component
class GalleryPhotoClientImpl implements GalleryPhotoClient {

    private static final String URL = "https://apis.data.go.kr/B551011/PhotoGalleryService1/galleryList1";
    /**
     * 실측 p95 가 0.35초(1,000건 페이지)라 크게 잡았다. 부팅 후 도는 배경 적재라 지연이 사용자에게 닿지
     * 않으므로 꼬리를 보수적으로 둔다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "offway";
    private static final Set<String> SUCCESS_CODES = Set.of("0000", "00");
    /** 제목순 정렬 — 페이지를 도는 동안 순서가 흔들리지 않게 고정한다. */
    private static final String ARRANGE_TITLE = "A";

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    GalleryPhotoClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public List<GalleryPhotoItem> findPage(int pageNo, int rows) {
        if (!props.dataGoKr().hasKey()) {
            log.info("관광사진 갤러리 키 없음 — 조회를 건너뜁니다");
            return List.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", "json")
                .queryParam("arrange", ARRANGE_TITLE)
                .queryParam("numOfRows", rows)
                .queryParam("pageNo", pageNo);
        try {
            return parse(call(builder));
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다.
            log.warn("관광사진 갤러리 조회 실패 pageNo={} cause={}", pageNo, e.getClass().getSimpleName());
            throw TourApiException.lookupFailed(e);
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(#165).
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.TOUR_GALLERY);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private List<GalleryPhotoItem> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode response = root.path("response");
        // 오류는 response 래퍼 없이 최상위로 오는 계열이다(#195 에서 실측).
        String resultCode = response.path("header").path("resultCode").asText(null);
        if (resultCode == null) {
            resultCode = root.path("resultCode").asText();
        }
        if (!SUCCESS_CODES.contains(resultCode)) {
            log.warn("관광사진 갤러리 조회가 실패 코드로 돌아왔습니다 resultCode={}", resultCode);
            throw new IllegalStateException("관광사진 갤러리 조회 실패 코드: " + resultCode);
        }
        JsonNode itemsNode = response.path("body").path("items");
        // 결과가 없으면 items 가 빈 문자열로 온다(data.go.kr 함정).
        if (itemsNode.isTextual()) {
            return List.of();
        }
        JsonNode items = itemsNode.path("item");
        List<GalleryPhotoItem> parsed = new ArrayList<>();
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

    /** 필수 값이 빠진 항목은 여기서 버린다 — 통과시키면 적재 시점에 터져 전량을 멈춘다(#195). */
    private static void addIfComplete(List<GalleryPhotoItem> parsed, JsonNode node) {
        GalleryPhotoItem item = toItem(node);
        if (!item.isComplete()) {
            log.warn("관광사진 항목에 필수 값이 없어 건너뜁니다 contentId={}", item.contentId());
            return;
        }
        parsed.add(item);
    }

    private static GalleryPhotoItem toItem(JsonNode node) {
        return new GalleryPhotoItem(
                node.path("galContentId").asText(),
                node.path("galTitle").asText(),
                node.path("galWebImageUrl").asText(),
                textOrNull(node, "galPhotographyMonth"),
                textOrNull(node, "galPhotographyLocation"),
                textOrNull(node, "galPhotographer"),
                textOrNull(node, "galSearchKeyword"));
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
