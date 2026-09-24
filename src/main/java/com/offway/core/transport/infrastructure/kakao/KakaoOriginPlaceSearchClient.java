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
import java.net.URI;
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

    /**
     * 주소 검색 — 키워드 검색보다 <b>먼저</b> 본다.
     *
     * <p><b>왜 둘을 쓰나.</b> 키워드 검색만 부르면 도로명주소를 친 사람이 그 자리의 건물·상호를 본다 —
     * {@code 서초구 남부순환로 2567} 에 "CONEST아파트"·"CU 양재역점"·"서초청년센터"가 떴다(실측
     * 2026-09-19). 자기 집 주소를 치고 남의 상호를 고르는 화면이 된다.
     *
     * <p>주소 검색은 같은 질의에 {@code 서울 서초구 남부순환로 2567} 을 준다. 그래서 <b>주소가 걸리면
     * 그것을 앞에 두고</b>, 모자란 자리만 키워드로 채운다.
     */
    private static final String ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json";

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

    /**
     * 카카오가 받는 {@code size} 의 상한(#599).
     *
     * <p><b>키워드 검색은 15 를 넘기면 400 이다.</b> 우리가 넘기는 값은 "허브로 채우고 남은 자리"
     * ({@code OriginHubCatalog.MAX_SUGGESTIONS} 가 상한이라 최대 20)라, <b>허브가 5건 미만 걸리는
     * 검색어에서 늘 한도를 넘었다</b> — 하필 그게 주소가 가장 필요한 검색어들이다("역삼"·"판교").
     *
     * <p>주소 검색은 상한이 더 커서 400 이 안 났고, 그래서 <b>둘 중 하나만 조용히 죽어 있었다.</b>
     *
     * <p>넘치면 거절하지 않고 <b>자른다</b>. 자리가 20 인데 15 만 채우는 것은 화면에 손해가 아니고,
     * 여기서 예외를 던지면 검색 한 번이 통째로 빈다.
     */
    private static final int MAX_KAKAO_SIZE = 15;

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
        // **주소를 먼저 묻고, 모자란 자리만 키워드로 채운다.** 주소로 다 채워지면 키워드는 아예
        // 부르지 않는다 — 자동완성은 글자마다 부르는 화면이라, 안 불러도 되는 호출을 줄이는 것이
        // 한도를 지키는 가장 확실한 방법이다.
        //
        // **둘의 실패를 따로 받는다.** 한 try 로 묶었더니 주소가 실패하면 키워드를 시도하지 않고,
        // 키워드가 실패하면 이미 받아 둔 주소 결과까지 버렸다 — 멀쩡한 결과를 버리는 것은 degrade 가
        // 아니라 손실이다.
        List<FoundPlace> found = new ArrayList<>(fetchOrEmpty(ADDRESS_URL, "주소", key));
        int room = key.limit() - found.size();
        if (room > 0) {
            for (FoundPlace place : fetchOrEmpty(KEYWORD_URL, "키워드", key.withLimit(room))) {
                // 주소 검색이 이미 준 지점은 건너뛴다 — 같은 곳이 이름만 달라 두 줄 뜨는 것을 막는다.
                if (found.stream().noneMatch(kept -> kept.coordinate().equals(place.coordinate()))) {
                    found.add(place);
                }
            }
        }
        if (found.isEmpty()) {
            // 빈 응답은 실패와 결과가 같다 — 성공 TTL 로 굳히지 않고 warn 을 남겨 재시도를 유도한다.
            log.warn("카카오 로컬 검색 결과 없음 — 짧은 TTL 로 둔다 length={}", key.query().length());
            return new ExternalDataCache.Loaded<>(List.of(), RETRY_TTL);
        }
        return new ExternalDataCache.Loaded<>(List.copyOf(found), SUCCESS_TTL);
    }

    /**
     * 한 오퍼레이션을 부르고, 실패하면 빈 목록으로 떨어진다.
     *
     * <p><b>검색어를 로그에 남기지 않는다.</b> 사용자가 친 원본이고, 출발지라서 사는 곳을 가리킨다 —
     * 위치를 수집하지 않으려고 이 기능을 만드는데 로그에 남기면 그 취지가 무너진다.
     *
     * <p>그래서 {@code RootCause.of} 가 아니라 {@link RootCause#label} 을 쓴다. {@code of} 는 예외
     * 메시지를 담는데 {@code WebClientResponseException} 메시지에는 <b>요청 URL 이 통째로</b> 들어올 수
     * 있고, 우리 요청 URL 에는 {@code ?query=<사용자가 친 말>} 이 붙어 있다. 마스킹은 비밀값만 가리고
     * 검색어는 못 가린다. {@code label} 은 상태코드나 클래스명만 남긴다.
     */
    private List<FoundPlace> fetchOrEmpty(String url, String what, CacheKey key) {
        try {
            return fetch(url, key.query(), key.limit());
        } catch (Exception e) {
            log.warn("카카오 로컬 {} 검색 실패 — 나머지 결과로 답한다 length={} cause={}",
                    what, key.query().length(), RootCause.label(e));
            return List.of();
        }
    }

    /**
     * 한 오퍼레이션을 부르고 파싱한다 — 콜 수를 세는 자리도 여기 하나뿐이다.
     *
     * <h2>URI 는 {@code String} 이 아니라 {@link URI} 로 넘긴다</h2>
     *
     * <p><b>여기서 한글 검색이 통째로 죽어 있었다</b>(#599). {@code toUriString()} 으로 만든
     * <b>이미 인코딩된 문자열</b>을 {@code uri(String)} 에 주면, WebClient 가 그것을 URI
     * <b>템플릿</b>으로 보고 <b>한 번 더 인코딩</b>한다 — {@code %EC} 가 {@code %25EC} 가 된다.
     *
     * <pre>
     *   만든 것 : ?query=%EC%84%9C%EC%9A%B8
     *   나간 것 : ?query=%25EC%2584%259C%25EC%259A%25B8
     * </pre>
     *
     * <p>카카오는 {@code 서울} 이 아니라 <b>{@code "%EC%84%9C%EC%9A%B8"} 이라는 글자</b>를 검색어로
     * 받는다. 그래서 주소·키워드 둘 다 아무것도 못 찾았다. ASCII 검색어는 {@code %} 가 없어 멀쩡히
     * 나가므로 <b>한글에서만</b> 터졌고, 응답은 200 + 빈 배열이라 아무 흔적도 안 남았다.
     *
     * <p>고친 것은 <b>마지막 한 단계뿐</b>이다 — {@code toUriString()} 대신 {@code toUri()} 로 받아
     * {@link URI} 를 넘긴다. {@code URI} 를 받는 오버로드는 <b>템플릿 확장을 거치지 않아</b> 두 번째
     * 인코딩이 일어나지 않는다. TourAPI 어댑터도 {@code URI} 로 넘긴다.
     *
     * <p><b>{@code encode()} 와 {@code build(true)} 를 같이 쓰지 않는다.</b> 앞엣것은 "네가 인코딩해
     * 달라", 뒤엣것은 "이미 인코딩돼 있다" 라 서로 어긋나고, 실제로 한글에서 {@code Invalid character}
     * 로 터진다(고치는 중에 밟았다).
     *
     * <h2>{@code size} 는 카카오 상한으로 자른다</h2>
     *
     * <p>{@link #MAX_KAKAO_SIZE} 참고. 넘기면 400 이고, 그 400 도 빈 목록으로 삼켜졌다.
     */
    private List<FoundPlace> fetch(String url, String query, int size)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        callRecorder.record(ExternalApi.KAKAO_LOCAL);
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("query", query)
                .queryParam("size", Math.min(size, MAX_KAKAO_SIZE))
                .build()
                .encode()
                .toUri();
        String body = webClient.get()
                .uri(uri)
                .header(AUTH_HEADER, AUTH_SCHEME + props.kakao().restApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        return parse(body);
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
            // 두 오퍼레이션의 모양이 다르다(실측 2026-09-19).
            //   keyword.json  place_name · road_address_name · address_name  (전부 평평한 문자열)
            //   address.json  place_name 없음 · road_address 가 **중첩 객체** · address_name
            String place = text(doc, "place_name");
            String road = text(doc, "road_address_name");
            if (road.isBlank()) {
                road = text(doc.path("road_address"), "address_name");
            }
            String jibun = text(doc, "address_name");
            String address = road.isBlank() ? jibun : road;
            // 장소명이 있으면 그것이 이름이고 주소는 부제목이다. 주소만 온 결과는 주소가 이름이 되고,
            // 부제목에는 **시도만** 남긴다 — 같은 문자열을 두 줄에 그리지 않고, 허브 행의 `area`
            // (서울·부산)와 표기를 맞춘다. 카카오가 이미 짧은 시도 표기로 준다("서울 서초구 …").
            String name = place.isBlank() ? address : place;
            String area = place.isBlank() ? firstToken(address) : address;
            if (name.isBlank()) {
                continue;
            }
            coordinateOf(doc).ifPresent(coordinate -> found.add(new FoundPlace(name, area, coordinate)));
        }
        return List.copyOf(found);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    /** 주소의 첫 낱말 — 시도 표기다("서울 서초구 남부순환로 2567" → "서울"). */
    private static String firstToken(String address) {
        int space = address.indexOf(' ');
        return space < 0 ? address : address.substring(0, space);
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
    private record CacheKey(String query, int limit) {

        /** 남은 자리만큼으로 줄인 키 — 키워드 검색이 주소가 채운 뒤의 자리 수만 받게 한다. */
        CacheKey withLimit(int newLimit) {
            return new CacheKey(query, newLimit);
        }
    }
}
