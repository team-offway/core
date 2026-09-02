package com.offway.core.weather.infrastructure.kma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.cache.ExternalDataCache;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.external.ExternalApiCachePolicy;
import com.offway.core.common.logging.RootCause;
import com.offway.core.weather.domain.Grid;
import com.offway.core.weather.domain.SkyState;
import com.offway.core.weather.domain.DailyWeather;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기상청 단기예보(동네예보) adapter — {@code VilageFcstInfoService_2.0/getVilageFcst}. data.go.kr 서비스라 TourAPI 와
 * 같은 serviceKey 를 쓴다. 위경도를 격자({@link Grid})로 바꿔 조회하고, 카테고리별 예보(TMN·TMX·SKY·POP)를 하루 요약으로
 * 집계한다.
 *
 * <p><b>base 는 반드시 {@code _2.0} 이다.</b> 구버전 {@code VilageFcstInfoService} 는 폐기돼
 * {@code NO_OPENAPI_SERVICE_ERROR}(해당 오픈API 서비스가 없거나 폐기됨)를 준다. 날씨는 부가 정보라 실패해도 코스는 200 으로
 * 나가므로, 이 경로가 틀리면 <b>아무도 모른 채 날씨가 계속 비어 있는다</b> — 실제로 그렇게 죽어 있었다(#128).
 * 스키마·파라미터는 구버전과 같아 base 만 다르다.
 *
 * <p>키 없음·실패·예보 범위 밖은 빈 Optional 로 폴백(날씨는 부가 정보). 발표시각(02·05·08·11·14·17·20·23시)의 최신본을 쓴다.
 */
@Slf4j
@Component
class KmaWeatherClientImpl implements KmaWeatherClient {

    private static final String URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    /**
     * 한 페이지에 담을 행 수. <b>페이지를 넘기지 않으므로 응답 전체가 여기 들어와야 한다.</b>
     *
     * <p>실측 907건·121KB({@code docs/external-api-inventory.md}). 예전 값 1000 은 여유가 10% 남짓이라,
     * 예보 시각이 한 칸만 늘어도 뒷날이 잘렸다. 날짜 하나만 훑던 시절에는 잘려도 그 날짜를 못 찾고 지나갔지만,
     * 지금은 <b>잘린 5일치가 세 시간 동안 캐시돼 모두에게 나간다</b> — 그래서 여유를 두 배 이상으로 벌린다.
     *
     * <p>응답이 커져도 {@code WebClient} 의 {@code maxInMemorySize}(2MB)에는 한참 못 미친다(121KB → 이론상 240KB).
     *
     * <p>그래도 넘칠 수 있으니 {@code parseAll} 이 {@code totalCount} 를 보고 경고를 남긴다 — 이 상한이
     * 부족해졌다는 사실 자체가 보여야 한다.
     */
    private static final int ROWS = 2000;

    private static final String SUCCESS_CODE = "00";
    private static final int[] BASE_HOURS = {23, 20, 17, 14, 11, 8, 5, 2};
    private static final int PUBLISH_DELAY_MIN = 10; // 발표 후 제공까지 지연
    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul"); // 발표일·발표시각은 KST 기준
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 하늘 상태의 대표 시각 — 밤 시간대가 하루를 대표하지 않게 정오를 쓴다. */
    private static final String NOON = "1200";

    /**
     * 발표 주기와 같다(3시간). 키에 발표시각이 들어 있어 새 슬롯은 <b>새 키</b>가 되므로, TTL 은 한 슬롯이
     * 살아 있는 동안만 덮으면 된다. 더 길게 잡아도 그 키를 다시 묻는 일이 없어 의미가 없다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(3);

    /**
     * 빈 응답(키 없음·호출 실패·파싱 실패)은 짧게만 붙든다.
     *
     * <p>값이 없다는 점에서 실패와 결과가 같은데 성공 TTL 로 누르면 세 시간 동안 날씨가 통째로 빈다.
     * 짧게 두고 다음 요청이 다시 묻게 한다(성능 규약의 "빈 응답을 성공으로 캐시하지 않는다").
     */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(1);

    /**
     * 캐시 키 상한 — <b>키 공간을 먼저 답한다</b>(성능 규약).
     *
     * <p>키는 (격자, 발표일+발표시각) 이고 둘 다 유한하다.
     *
     * <ul>
     *   <li>격자: 좌표가 5km 격자로 접힌다. 부르는 좌표는 89개 인구감소지역의 코스 중심이라, 지역당 서로 다른
     *       격자가 몇 개씩 나와도 수백 단위에서 멈춘다
     *   <li>발표시각: 조회는 늘 <b>최신 슬롯</b>만 쓰므로 살아 있는 세대는 사실상 하나다. 슬롯이 바뀌는 순간
     *       직전 세대가 TTL 만큼 남아 잠깐 둘이 된다
     * </ul>
     *
     * <p>89 지역 × 격자 4 × 세대 2 ≈ 700 에 여유를 얹었다. 넘치면 프리미티브가 오래된 것부터 축출한다.
     */
    private static final int MAX_CACHED_FORECASTS = 1024;

    /** loader 가 단기예보 <b>단일 호출</b>(timeout 6초)이라 여유 1초를 얹었다. */
    private static final Duration FIRST_LOAD_WAIT = Duration.ofSeconds(7);

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 캐시를 켜고 끄는 스위치(#403). 조회마다 물어, 운영 중 바뀐 값도 곧바로 듣는다. */
    private final ExternalApiCachePolicy cachePolicy;

    /**
     * 격자·발표시각 단위 예보 캐시 — 응답 하나가 담은 <b>5일치 전부</b>를 보관한다.
     *
     * <p><b>왜 어댑터인가.</b> 이 레포의 다른 캐시는 전부 서비스에 있다. "응답 하나가 여러 날을 담는다" 는
     * 이 API 의 사정이지 도메인 사실이 아니라, 포트를 {@code Map<LocalDate, DailyWeather>} 로 넓히면 그
     * 사정이 호출자와 테스트 stub 까지 번진다. 부르는 쪽은 "이 날짜의 날씨" 만 알면 된다.
     */
    private final ExternalDataCache<ForecastKey, Map<LocalDate, DailyWeather>> cache =
            new ExternalDataCache<>(MAX_CACHED_FORECASTS, FIRST_LOAD_WAIT,
                    this::cacheEnabled);

    KmaWeatherClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder, ExternalApiCachePolicy cachePolicy) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
        this.cachePolicy = cachePolicy;
    }

    /** 같은 격자·같은 발표시각이면 응답이 완전히 같다 — 그 단위가 곧 캐시 키다. */
    private record ForecastKey(int nx, int ny, String baseDate, String baseTime) {}

    @Override
    public Optional<DailyWeather> dailyForecast(double lat, double lng, LocalDate date) {
        if (!props.dataGoKr().hasKey() || date == null) {
            return Optional.empty();
        }
        Grid grid = Grid.from(lat, lng);
        String[] base = baseDateTime(LocalDateTime.now(KMA_ZONE));
        ForecastKey key = new ForecastKey(grid.nx(), grid.ny(), base[0], base[1]);

        // 응답 하나가 5일치를 담으므로 Day 마다 부르지 않는다 — 첫 Day 가 채운 값을 나머지가 그대로 쓴다(#207).
        Map<LocalDate, DailyWeather> byDate = cache.get(key, (k, stale) -> load(k), Map.of());
        return Optional.ofNullable(byDate.get(date));
    }

    @Override
    public void evictCache() {
        cache.evictAll();
    }

    private ExternalDataCache.Loaded<Map<LocalDate, DailyWeather>> load(ForecastKey key) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("base_date", key.baseDate())
                .queryParam("base_time", key.baseTime())
                .queryParam("nx", key.nx())
                .queryParam("ny", key.ny());
        try {
            Map<LocalDate, DailyWeather> parsed = parseAll(call(builder));
            // 빈 결과를 성공 TTL 로 누르면 세 시간 동안 날씨가 통째로 빈다.
            return new ExternalDataCache.Loaded<>(parsed, parsed.isEmpty() ? EMPTY_TTL : CACHE_TTL);
        } catch (Exception e) {
            log.warn("기상청 단기예보 조회 실패 — 날씨 생략 nx={} ny={} cause={}",
                    key.nx(), key.ny(), RootCause.of(e));
            return new ExternalDataCache.Loaded<>(Map.of(), EMPTY_TTL);
        }
    }

    private String call(UriComponentsBuilder builder) {
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.KMA_WEATHER);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    /** 발표시각 중 (현재-10분) 이하의 최신 슬롯. 02시 이전이면 전날 23시. */
    private String[] baseDateTime(LocalDateTime now) {
        LocalDateTime t = now.minusMinutes(PUBLISH_DELAY_MIN);
        for (int hour : BASE_HOURS) {
            if (t.getHour() >= hour) {
                return new String[] {t.toLocalDate().format(YMD), String.format("%02d00", hour)};
            }
        }
        return new String[] {t.toLocalDate().minusDays(1).format(YMD), "2300"};
    }

    /**
     * 응답에 담긴 <b>모든 날짜</b>를 하루 요약으로 접는다.
     *
     * <p>예전에는 날짜 하나를 인자로 받아 나머지를 버렸다. 응답 131KB 에 5일치가 다 들어 있는데 Day 마다
     * 다시 받아 나흘치를 버리는 셈이었다(#207).
     */
    private Map<LocalDate, DailyWeather> parseAll(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODE.equals(resultCode)) {
            // **성공 HTTP 에 실패 코드로 오는 경우가 있다.** 일일 쿼터 소진(22)·서비스 폐기가 그렇다.
            // 예외가 아니라 아무 흔적이 없어, 여기서 남기지 않으면 코스마다 날씨가 조용히 비고 아무도 모른다
            // (#128 이 정확히 그렇게 죽어 있었다). 제공기관이 주는 사유를 그대로 옮긴다.
            log.warn("기상청 단기예보 실패 코드 — 날씨 생략 resultCode={} msg={}",
                    resultCode, response.path("header").path("resultMsg").asText());
            return Map.of();
        }

        // 페이지를 넘기지 않으므로 한 페이지에 다 들어와야 한다. 실측 907건(외부 API 인벤토리)이라 여유가
        // 10% 남짓인데, 넘치면 마지막 날이 잘린 채 세 시간 동안 모두에게 나간다 — 잘렸다는 사실만은 남긴다.
        int totalCount = response.path("body").path("totalCount").asInt();
        boolean truncated = totalCount > ROWS;
        if (truncated) {
            log.warn("기상청 단기예보가 한 페이지를 넘었습니다 — 마지막 날을 버립니다 totalCount={} rows={}",
                    totalCount, ROWS);
        }

        Map<String, DayAccumulator> byDate = new LinkedHashMap<>();
        for (JsonNode item : response.path("body").path("items").path("item")) {
            String fcstDate = item.path("fcstDate").asText();
            if (fcstDate.isEmpty()) {
                continue;
            }
            byDate.computeIfAbsent(fcstDate, d -> new DayAccumulator())
                    .add(item.path("category").asText(),
                            item.path("fcstValue").asText(),
                            item.path("fcstTime").asText());
        }

        Map<LocalDate, DailyWeather> result = new LinkedHashMap<>();
        byDate.forEach((ymd, accumulator) -> {
            if (!accumulator.hasAny()) {
                return;
            }
            // **날짜 하나가 깨져도 나머지 날은 살린다.** 예전에는 대상 날짜만 훑어 이런 값이 그냥 안 맞고
            // 지나갔는데, 이제는 응답 전체를 접으므로 여기서 던지면 멀쩡한 나흘까지 함께 버려진다.
            LocalDate date = parseDate(ymd);
            if (date == null) {
                return;
            }
            result.put(date, accumulator.toDailyWeather(date));
        });

        // **잘렸으면 마지막 날은 버린다.** 응답은 날짜순이라 잘린 자리가 곧 마지막 날이고, 그 날은 카테고리가
        // 덜 온 반쪽이다(POP 은 있는데 TMN·TMX 가 없는 식). `hasAny()` 는 true 라 그대로 두면 기온 없는 예보가
        // 정상인 척 세 시간 동안 나간다 — 없는 것이 반쪽보다 정직하다.
        //
        // 다시 부르지는 않는다. 잘림은 응답 구조에서 오는 것이라 재시도해도 같은 답이 오고, 쿼터만 태운다.
        // 상한을 올려야 한다는 신호는 위 warn 이 남긴다.
        if (truncated && !result.isEmpty()) {
            LocalDate last = result.keySet().stream().max(LocalDate::compareTo).orElseThrow();
            result.remove(last);
        }
        return Map.copyOf(result);
    }

    /** 한 날짜의 카테고리별 예보를 하루 요약으로 모은다. */
    private static final class DayAccumulator {

        private Integer minTemp;
        private Integer maxTemp;
        private String skyCode;
        /** 강수는 하루 중 <b>언제 오든</b> 살린다 — 정오만 보면 "오전 맑고 오후 비" 가 맑음으로 나간다. */
        private SkyState precipitation = SkyState.UNKNOWN;
        private Integer rainProb;
        private boolean any;

        void add(String category, String value, String fcstTime) {
            switch (category) {
                case "TMN" -> {
                    minTemp = toInt(value);
                    any = true;
                }
                case "TMX" -> {
                    maxTemp = toInt(value);
                    any = true;
                }
                case "POP" -> {
                    rainProb = maxOf(rainProb, toInt(value));
                    any = true;
                }
                case "SKY" -> {
                    if (skyCode == null || NOON.equals(fcstTime)) {
                        skyCode = value; // 정오값을 대표로 — 밤 시간대가 하루를 대표하지 않게
                    }
                    any = true;
                }
                case "PTY" -> {
                    // 하늘과 달리 정오를 고르지 않는다. 비·눈은 그날 한 번이라도 오면 챙겨야 한다.
                    precipitation = precipitation.worseOf(SkyState.from(null, value));
                    any = true;
                }
                default -> { /* 사용하지 않는 카테고리(TMP 등)는 결과 유무에 영향 없음 */ }
            }
        }

        boolean hasAny() {
            return any;
        }

        DailyWeather toDailyWeather(LocalDate date) {
            // 강수가 있으면 하늘 상태를 덮는다(#135). 없으면 정오 하늘이 답이다.
            SkyState sky = precipitation.hasPrecipitation() ? precipitation : SkyState.from(skyCode, null);
            return new DailyWeather(date, minTemp, maxTemp, sky, rainProb);
        }
    }

    /**
     * 예보일(yyyyMMdd). 형식이 어긋나면 그 날짜만 버린다.
     *
     * <p>warn 인 이유: 이건 데이터 하나가 이상한 것이 아니라 <b>제공기관이 형식을 바꿨다</b>는 신호일 수 있다.
     * 그 경우 모든 날이 한꺼번에 빠지는데, debug 로 두면 날씨가 통째로 사라진 채 흔적이 없다.
     */
    private static LocalDate parseDate(String ymd) {
        try {
            return LocalDate.parse(ymd, YMD);
        } catch (DateTimeParseException e) {
            log.warn("기상청 예보일 형식이 어긋나 건너뜁니다 fcstDate={}", ymd);
            return null;
        }
    }

    private static Integer toInt(String value) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.max(current, candidate);
    }

    /**
     * 캐시를 지금 써도 되나(#403).
     *
     * <p>람다로 필드를 직접 읽지 않고 메서드 참조를 쓰는 이유 — 캐시 필드의 초기화식은 생성자가
     * {@code cachePolicy} 를 넣기 <b>전에</b> 돌아서, 거기서 blank final 을 읽으면 컴파일이 막힌다.
     * 메서드 본문은 그때 읽히지 않는다.
     */
    private boolean cacheEnabled() {
        return cachePolicy.cacheEnabled(ExternalApi.KMA_WEATHER);
    }
}
