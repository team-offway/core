package com.offway.core.trip.infrastructure.festival;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import com.offway.core.common.logging.RootCause;
import com.offway.core.trip.domain.TourApiException;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestival;
import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 전국문화축제표준데이터 adapter(#433) — <b>오픈API 가 아니라 파일</b>이다.
 *
 * <h2>왜 파일인가</h2>
 *
 * <p>처음에는 {@code api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api} 를 불렀다. 그 주소는
 * 실재한다 — 키 없이 부르면 {@code SERVICE_KEY_IS_NULL} 이 오고, 없는 API 는
 * {@code NO_OPENAPI_SERVICE_ERROR} 를 준다. 문제는 <b>활용신청할 데이터셋 페이지가 없다</b>는 것이다.
 * 포털의 오픈API 목록에는 개별 지자체 것(광양·괴산·대전·울산…)만 있고 전국 통합본이 없다.
 *
 * <p>그래서 운영에서 계속 {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}(403) 였고 축제가 <b>0건</b>이었다.
 * 신청할 곳이 없으니 기다려도 풀리지 않는다.
 *
 * <p>표준데이터 페이지({@code data.go.kr/data/15013104/standard.do})의 <b>다운로드 버튼이 실제로 부르는
 * 주소</b>로 옮겼다. 인증키가 필요 없고 전량이 한 번에 온다.
 *
 * <h2>파라미터 규칙 — 실측으로 확인했다</h2>
 *
 * <ul>
 *   <li>{@code totalCount} 는 <b>값이 무엇이든 상관없지만 있어야 한다.</b> 빼면 404 다. 우리는 미리
 *       알 수 없으므로 넉넉한 상수를 넣는다 — 응답 크기를 정하는 것은 {@code perPage} 다.
 *   <li>{@code colNmList} 는 <b>필수</b>다. 빼면 0바이트가 온다.
 *   <li>{@code perPage} 를 크게 주면 페이지네이션이 필요 없다. 실측 1,305건이 약 900KB 다.
 * </ul>
 *
 * <h2>응답은 영문 키 배열이다</h2>
 *
 * <p>브라우저로 받은 파일은 {@code {fields, records}} 에 한글 키지만, 그건 화면이 후처리한 것이다.
 * <b>직접 부르면 영문 대문자 키의 평평한 배열</b>이 온다 — {@code FSTVL_NM}·{@code LATITUDE} 처럼.
 * 예전 코드가 {@code fstvlNm} 같은 camelCase 를 기대한 것은 명세를 못 봐서 한 추측이었고, 틀렸다.
 *
 * <p><b>못 찾으면 조용히 넘어가지 않는다</b> — 행은 왔는데 축제명을 하나도 못 읽으면 던진다. 필드명이
 * 틀렸을 때 "축제 0건" 이 정상처럼 보이면 안 되고, 그보다 나쁜 것은 그것이 <b>성공한 빈 결과로</b>
 * 세어져 취소 정리가 이번 회차를 온전한 것으로 판정하는 것이다 — 그러면 못 읽은 축제들이 지워진다.
 *
 * <p><b>"이름을 못 읽음" 과 "좌표가 없어 제외" 는 다르다.</b> 후자는 1,305건 중 225건(17%)이나 되는
 * 정상 상황이라, 그걸로 던지면 멀쩡한 적재가 멈춘다. 이름을 읽은 행 수를 따로 세어 가른다.
 */
@Slf4j
@Component
class FestivalStandardClientImpl implements FestivalStandardClient {

    /** 표준데이터 파일 주소 — 다운로드 버튼이 부르는 것과 같다. */
    private static final String BASE = "https://www.data.go.kr/download/standard.json";

    /** 이 표준데이터의 포털 식별자와 테이블명. 둘 다 주소에 필요하다. */
    private static final String PUBLIC_DATA_PK = "15013104";
    private static final String SVC_TABLE = "tn_pubr_public_cltur_fstvl_svc";

    /**
     * 한 번에 받을 행 수 — 전량을 덮고도 남게 잡는다.
     *
     * <p>실측 1,305건이라 여유가 크다. 원본이 두 배로 늘어도 페이지를 나눌 필요가 없고, 나누면
     * 그만큼 "일부만 받은 회차" 를 다루는 분기가 생긴다.
     */
    private static final int PER_PAGE = 10_000;

    /**
     * {@code totalCount} 자리 — <b>값은 안 쓰이지만 없으면 404 다.</b>
     *
     * <p>화면이 자기가 아는 총건수를 그대로 실어 보내는 파라미터인데, 서버는 그 값을 검증하지 않는다
     * (1 을 넣어도 1,305건이 온다). 우리는 미리 알 수 없으므로 상한만 넉넉히 둔다.
     */
    private static final int TOTAL_COUNT_PLACEHOLDER = 1_000_000;

    /**
     * 이 한 건의 기본 상한.
     *
     * <p>전량을 한 번에 받으므로 페이지 하나보다 오래 걸린다 — 실측 900KB 에 1초 안팎이었다.
     * 원본이 커지는 것을 감안해 여유를 둔다.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** 날짜 형식 — {@code yyyy-MM-dd} 로 온다. 공백·빈칸이 섞여 오는 행이 있다. */
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── 응답 필드명. 2026-09-07 실호출로 확정했다 ──────────────────────
    private static final String F_NAME = "FSTVL_NM";
    private static final String F_START = "FSTVL_START_DATE";
    private static final String F_END = "FSTVL_END_DATE";
    private static final String F_CONTENT = "FSTVL_CO";
    private static final String F_VENUE = "OPAR";
    private static final String F_HOST = "MNNST_NM";
    private static final String F_TEL = "PHONE_NUMBER";
    private static final String F_HOMEPAGE = "HOMEPAGE_URL";
    private static final String F_ROAD_ADDRESS = "RDNMADR";
    private static final String F_JIBUN_ADDRESS = "LNMADR";
    private static final String F_LAT = "LATITUDE";
    private static final String F_LNG = "LONGITUDE";

    /** 요청에 실을 컬럼 목록 — 빼면 응답이 0바이트다. */
    private static final List<String> COLUMNS = List.of(
            F_NAME, F_VENUE, F_START, F_END, F_CONTENT, F_HOST,
            "AUSPC_INSTT_NM", "SUPRT_INSTT_NM", F_TEL, F_HOMEPAGE, "RELATE_INFO",
            F_ROAD_ADDRESS, F_JIBUN_ADDRESS, F_LAT, F_LNG, "REFERENCE_DATE");

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    FestivalStandardClientImpl(WebClient externalWebClient, ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.callRecorder = callRecorder;
    }

    @Override
    public StandardFestivalResult findAll(Duration maxWait) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE)
                .queryParam("publicDataPk", PUBLIC_DATA_PK)
                .queryParam("svcTableNm", SVC_TABLE)
                .queryParam("perPage", PER_PAGE)
                .queryParam("page", 1)
                .queryParam("totalCount", TOTAL_COUNT_PLACEHOLDER);
        COLUMNS.forEach(column -> builder.queryParam("colNmList", column));
        URI uri = builder.build(true).toUri();

        Duration wait = maxWait.compareTo(TIMEOUT) < 0 ? maxWait : TIMEOUT;
        try {
            // 실호출 직전에 센다. 인증키를 안 쓰므로 한도와 무관하지만, 우리가 얼마나 부르는지는 남긴다.
            callRecorder.record(ExternalApi.FESTIVAL_STANDARD);
            String body = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(wait)
                    .block();
            return parse(body);
        } catch (Exception e) {
            log.warn("문화축제표준데이터 조회 실패 cause={}", RootCause.of(e));
            throw TourApiException.festivalStandardLookupFailed(e);
        }
    }

    private StandardFestivalResult parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) {
            // 배열이 아니면 오류 응답이다. 파일 주소는 성공하면 언제나 평평한 배열을 준다.
            throw new IllegalStateException(
                    "문화축제표준데이터 응답이 배열이 아닙니다: " + body.substring(0, Math.min(200, body.length())));
        }

        List<StandardFestival> parsed = new ArrayList<>();
        int rows = 0;
        int namedRows = 0;
        for (JsonNode node : root) {
            rows++;
            String name = text(node, F_NAME);
            if (name == null) {
                continue; // 이름조차 못 읽었다 — 필드명 오류 후보다. 아래에서 함께 판정한다
            }
            namedRows++;
            StandardFestival festival = toFestival(node, name);
            if (festival != null) {
                parsed.add(festival);
            }
        }

        // **행은 왔는데 이름을 하나도 못 읽었다 = 필드명이 틀렸다.** 던져야 호출자가 실패로 세고
        // 취소 정리를 건너뛴다 — 빈 결과로 돌려주면 멀쩡한 축제들이 취소로 간주돼 지워진다.
        if (rows > 0 && namedRows == 0) {
            throw new IllegalStateException(
                    "문화축제표준데이터 %d행을 받았지만 축제명을 하나도 읽지 못했습니다 — 응답 필드명을 확인하세요 (기대: %s) 실제 키: %s"
                            .formatted(rows, F_NAME, fieldNamesOf(root)));
        }
        if (namedRows > parsed.size()) {
            // 이름은 읽혔는데 좌표·기간이 없어 빠진 것들이다. 실측 1,305건 중 225건이 좌표 없음이라
            // 정상이지만, 갑자기 늘면 원본이 바뀐 신호라 남긴다.
            log.info("문화축제표준데이터 좌표·기간이 없어 {}건을 뺐습니다", namedRows - parsed.size());
        }
        log.debug("문화축제표준데이터 받은행={} 이름읽음={} 쓸수있음={}", rows, namedRows, parsed.size());
        return new StandardFestivalResult(parsed, rows);
    }

    /** 첫 행의 키 목록 — 필드명이 틀렸을 때 무엇으로 고쳐야 하는지 로그가 바로 답하게 한다. */
    private static String fieldNamesOf(JsonNode rows) {
        List<String> names = new ArrayList<>();
        rows.path(0).fieldNames().forEachRemaining(names::add);
        return String.join(", ", names);
    }

    /**
     * 한 행을 옮긴다. 좌표·기간이 없거나 형식이 어긋나면 <b>그 한 건만</b> 건너뛴다(null).
     *
     * @param name 호출자가 이미 읽어 둔 축제명 — 여기서 다시 읽지 않는다
     */
    private static StandardFestival toFestival(JsonNode node, String name) {
        String address = firstNonBlank(text(node, F_ROAD_ADDRESS), text(node, F_JIBUN_ADDRESS));
        StandardFestival festival = new StandardFestival(
                name,
                text(node, F_VENUE),
                address,
                sigunguOf(address),
                decimal(node, F_LAT),
                decimal(node, F_LNG),
                date(node, F_START),
                date(node, F_END),
                text(node, F_CONTENT),
                text(node, F_HOST),
                text(node, F_TEL),
                text(node, F_HOMEPAGE));
        return festival.isUsable() ? festival : null;
    }

    /**
     * 주소에서 시군구명을 뽑는다 — "경상북도 안동시 ..." 의 둘째 토큰.
     *
     * <p>주소 체계가 "시도 시군구" 로 시작하는 것은 도로명·지번 둘 다 같다. 같은 이름이 둘인 시군구를
     * 가르는 일은 호출자가 주소 전체로 한다(#502).
     */
    private static String sigunguOf(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] tokens = address.trim().split("\\s+");
        return tokens.length >= 2 ? tokens[1] : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
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

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
