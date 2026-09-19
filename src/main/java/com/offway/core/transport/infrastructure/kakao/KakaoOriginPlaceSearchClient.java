package com.offway.core.transport.infrastructure.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.geo.Coordinate;
import com.offway.core.common.logging.RootCause;
import com.offway.core.transport.infrastructure.kakao.dto.FoundPlace;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로컬 키워드 검색 adapter — 출발지로 고를 주소·장소를 찾는다(#590).
 *
 * <p>인증은 {@code Authorization: KakaoAK <REST 키>} 헤더. 키가 없으면 외부 호출 없이 빈 목록으로
 * 떨어진다(로컬 실행성 규칙).
 *
 * <h2>왜 캐시가 필수인가</h2>
 *
 * 자동완성은 <b>글자마다 부르는 화면</b>이다. 같은 검색어가 반복되고("서울" 을 치는 사람은 많다) 값은
 * 거의 변하지 않는다 — 주소와 지명은 몇 년 단위로 바뀐다. 그런데 <b>키 공간이 무한하다</b>: 검색어는
 * 사용자가 만드는 문자열이라 캐시가 끝없이 자랄 수 있다. TTL 은 값의 신선도만 관리하고 엔트리를 지우지
 * 않으므로, <b>엔트리 수 상한을 함께</b> 둔다(캐시 키 공간의 상한을 먼저 정하라는 규칙 그대로).
 *
 * <h2>빈 결과를 성공으로 캐시하지 않는다</h2>
 *
 * 없는 지명을 친 것과 외부가 조용히 빈 응답을 준 것은 <b>결과가 같다</b>. 성공 TTL 로 눌러 두면 그
 * 무의미한 상태가 그만큼 굳으므로, 실패와 같은 짧은 TTL 로 두어 다시 묻게 한다.
 *
 * <h2>결과를 DB 에 저장하지 않는다</h2>
 *
 * 카카오 로컬은 결과 저장이 약관으로 막혀 있다. 그래서 응답으로만 흘리고 캐시는 <b>메모리</b>에 둔다 —
 * 이 클래스가 그 경계를 지키는 유일한 자리다.
 */
@Slf4j
@Component
class KakaoOriginPlaceSearchClient implements OriginPlaceSearchClient {

    private static final String KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_SCHEME = "KakaoAK ";

    /**
     * 호출 상한.
     *
     * <p>자동완성은 사용자가 글자를 치는 동안 기다리는 자리라 길게 잡을 수 없다 — 3초를 넘기면 사용자가
     * 이미 다음 글자를 쳤고, 그 응답은 버려진다. 늦으면 허브 목록만으로 답하는 편이 낫다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /**
     * 캐시 엔트리 상한 — 검색어가 키라서 <b>무한히 자랄 수 있는</b> 키 공간이다.
     *
     * <p>2,000 은 "자주 쓰이는 검색어" 를 담기에 넉넉하고(전국 시군구 229 + 주요 지명 수백), 넘치면
     * 만료가 가까운 것부터 버려진다. 값이 작아도 손해가 아니다 — 캐시를 놓치면 외부를 한 번 더 부를 뿐이다.
     */
    private static final int MAX_CACHE_ENTRIES = 2_000;

    /** 주소·지명은 몇 년 단위로 바뀐다. 하루면 충분히 짧고, 하루면 충분히 길다. */
    private static final Duration SUCCESS_TTL = Duration.ofHours(24);

    /** 실패·빈 결과는 짧게 — 다시 묻게 한다. */
    private static final Duration RETRY_TTL = Duration.ofMinutes(5);

    /** 첫 적재 대기 상한 — 같은 검색어에 동시 요청이 몰렸을 때 늦은 쪽이 기다릴 시간. */
    private static final Duration WAIT_FOR_FIRST_LOAD = Duration.ofSeconds(4);

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExternalDataCache<CacheKey, List<FoundPlace>> cache =
            new ExternalDataCache<>(MAX_CACHE_ENTRIES, WAIT_FOR_FIRST_LOAD);

    KakaoOriginPlaceSearchClient(
            WebClient externalWebClient,
            ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public List<FoundPlace> search(String query, int limit) {
        if (!props.kakao().hasKey() || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        return cache.get(new CacheKey(query.trim(), limit), (key, stale) -> load(key), List.of());
    }

    private ExternalDataCache.Loaded<List<FoundPlace>> load(CacheKey key) {
        try {
            callRecorder.record(ExternalApi.KAKAO_LOCAL);
            String uri = UriComponentsBuilder.fromUriString(KEYWORD_URL)
                    .queryParam("query", key.query())
                    .queryParam("size", key.limit())
                    .build()
                    .encode()
                    .toUriString();
            String body = webClient.get()
                    .uri(uri)
                    .header(AUTH_HEADER, AUTH_SCHEME + props.kakao().restApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            List<FoundPlace> found = parse(body);
            if (found.isEmpty()) {
                // 빈 응답은 실패와 결과가 같다 — 성공 TTL 로 굳히지 않고 warn 을 남겨 재시도를 유도한다.
                log.warn("카카오 로컬 검색 결과 없음 — 짧은 TTL 로 둔다 length={}", key.query().length());
                return new ExternalDataCache.Loaded<>(List.of(), RETRY_TTL);
            }
            return new ExternalDataCache.Loaded<>(found, SUCCESS_TTL);
        } catch (Exception e) {
            // **검색어를 로그에 남기지 않는다.** 사용자가 친 원본이고, 출발지라서 사는 곳을 가리킨다 —
            // 위치를 수집하지 않으려고 이 기능을 만드는데 로그에 남기면 그 취지가 무너진다.
            log.warn("카카오 로컬 검색 실패 — 허브 목록만으로 답한다 length={} cause={}",
                    key.query().length(), RootCause.of(e));
            return new ExternalDataCache.Loaded<>(List.of(), RETRY_TTL);
        }
    }

    /**
     * 응답에서 쓸 수 있는 것만 고른다.
     *
     * <p>좌표가 없거나 숫자로 안 읽히는 항목은 버린다 — 출발지는 좌표가 있어야 최근접 허브를 찾을 수
     * 있고, 좌표 없는 제안은 고르는 순간 코스가 degrade 된다.
     */
    private List<FoundPlace> parse(String body) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode documents = objectMapper.readTree(body).path("documents");
        List<FoundPlace> found = new ArrayList<>();
        for (JsonNode doc : documents) {
            String name = text(doc, "place_name");
            String road = text(doc, "road_address_name");
            String jibun = text(doc, "address_name");
            String address = road.isBlank() ? jibun : road;
            String label = name.isBlank() ? address : name;
            if (label.isBlank()) {
                continue;
            }
            coordinateOf(doc).ifPresent(coordinate -> found.add(new FoundPlace(label, address, coordinate)));
        }
        return List.copyOf(found);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    /** 카카오는 {@code x} 가 경도, {@code y} 가 위도다(문자열로 온다). */
    private static java.util.Optional<Coordinate> coordinateOf(JsonNode doc) {
        try {
            String x = text(doc, "x");
            String y = text(doc, "y");
            if (x.isBlank() || y.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Coordinate(Double.parseDouble(y), Double.parseDouble(x)));
        } catch (IllegalArgumentException e) {
            // 숫자가 아니거나 좌표 범위를 벗어났다 — 그 항목만 버리고 나머지는 쓴다.
            return java.util.Optional.empty();
        }
    }

    /**
     * 캐시 키 — 검색어와 건수 둘 다 키다.
     *
     * <p>건수를 빼면 {@code size=5} 로 받은 값이 {@code size=20} 요청에 그대로 나가 목록이 조용히 짧아진다.
     */
    private record CacheKey(String query, int limit) {}
}
