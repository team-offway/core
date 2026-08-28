package com.offway.core.weather.infrastructure.kma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.weather.domain.SigunguKey;
import com.offway.core.weather.domain.TourClimateGrade;
import com.offway.core.weather.domain.TourClimateIndex;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기상청 관광기후지수 adapter — {@code TourStnInfoService1/getCityTourClmIdx1}(#130).
 *
 * <p><b>base 의 {@code 1} 은 하나다.</b> data.go.kr 포털이 {@code TourStnInfoService11} 로 표기하고 있으나 그 경로는
 * 404 다(실측 확인). 오퍼레이션 쪽 {@code 1} 과 붙어 보이는 표기 오류다.
 *
 * <p><b>{@code DAY} 는 며칠 뒤가 아니라 며칠치다.</b> {@code DAY=9} 를 주면 D+9 하루가 아니라 <b>D+1 부터 D+9 까지
 * 아홉 날이 한꺼번에</b> 온다(실측: {@code totalCount} 가 236 × DAY 로 는다). 오프셋으로 착각해 날짜마다 부르면
 * 같은 데이터를 아홉 번 받는다. 그래서 한 번만 부르고 날짜별로 나눈다.
 *
 * <p><b>{@code CITY_AREA_ID} 도 주지 않는다.</b> 지정하면 특정 시군구만 오는데, 89개 지역을 각각 부르는 것보다
 * 전국을 한 번 받아 나누는 편이 낫다.
 */
@Slf4j
@Component
class TourClimateIndexClientImpl implements TourClimateIndexClient {

    private static final String URL = "https://apis.data.go.kr/1360000/TourStnInfoService1/getCityTourClmIdx1";

    /** 실측 2.5초(347KB)라 여유를 크게 얹었다 — 우리가 받는 응답 중 가장 크다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /**
     * 한 번에 받을 항목 수. 실측 9일 × 236곳 = 2,124건이라 여유를 얹었다.
     *
     * <p>모자라면 <b>뒷 날짜가 통째로 잘린다</b> — 응답이 날짜순이라 부족분이 특정 날짜에 몰린다. 페이지를 나눠 도는
     * 대신 한 번에 받는 이유는, 347KB 가 {@code WebClient} 상한(2MB) 안에 넉넉히 들어오기 때문이다.
     */
    private static final int ROWS = 3000;

    /** 조회 기준 시각 — 하루 단위 값이라 발표 시각을 고정한다. */
    private static final String BASE_HOUR = "06";

    /** 지수 산출 범위(시간). 하루치를 합산한 값을 받는다. */
    private static final String HOUR_SPAN = "24";

    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 응답 {@code tm}({@code "2026-08-04 00:00"}) 앞부분의 날짜 길이. */
    private static final int YMD_DASHED_LENGTH = 10;

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TourClimateIndexClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public Map<LocalDate, Map<SigunguKey, TourClimateIndex>> forecast() {
        if (!props.dataGoKr().hasKey()) {
            return Map.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("CURRENT_DATE", LocalDate.now(KMA_ZONE).format(YMD) + BASE_HOUR)
                .queryParam("HOUR", HOUR_SPAN)
                .queryParam("DAY", TourClimateIndex.LAST_DAY);
        try {
            return parse(call(builder));
        } catch (Exception e) {
            log.warn("기상청 관광기후지수 조회 실패 — 지수 생략 cause={}", RootCause.of(e));
            return Map.of();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — 다른 data.go.kr 어댑터와 같은 규약.
        URI uri = builder.build(true).toUri();
        // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
        callRecorder.record(ExternalApi.KMA_WEATHER);
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    private Map<LocalDate, Map<SigunguKey, TourClimateIndex>> parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        JsonNode header = response.path("header");
        if (!"00".equals(header.path("resultCode").asText())) {
            // 서비스키 문제·쿼터 초과가 여기로 온다. 로그가 없으면 지수가 비는 이유를 나중에 못 찾는다.
            // resultMsg 까지 남긴다 — 코드만으로는 어떤 실패인지 갈리지 않는다.
            log.warn("관광기후지수 응답이 비정상 resultCode 입니다 — 지수 없음 code={} msg={}",
                    header.path("resultCode").asText(), header.path("resultMsg").asText());
            return Map.of();
        }
        JsonNode items = response.path("body").path("items").path("item");
        Map<LocalDate, Map<SigunguKey, TourClimateIndex>> byDate = new HashMap<>();
        int unreadable = 0;
        for (JsonNode item : items) {
            LocalDate date = dateOf(item);
            SigunguKey key = SigunguKey.of(item.path("doName").asText(null), item.path("cityName").asText(null));
            if (date == null || key == null) {
                unreadable++;
                continue;
            }
            byDate.computeIfAbsent(date, ignored -> new HashMap<>())
                    .put(key, new TourClimateIndex(
                            date,
                            item.path("kmaTci").asDouble(),
                            TourClimateGrade.fromLabel(item.path("TCI_GRADE").asText(null))));
        }
        if (byDate.isEmpty()) {
            // resultCode 는 성공인데 결과가 비어 오는 흔한 경우. 조용히 넘기면 아무 흔적도 안 남는다.
            log.warn("기상청 관광기후지수 응답이 비어 왔습니다 — 지수 생략");
            return Map.of();
        }
        if (unreadable > 0) {
            // 날짜·시군구명이 빠진 항목. 응답 스키마가 바뀌면 여기가 늘어난다.
            log.warn("관광기후지수 {}건을 읽지 못했습니다 — 스키마 변경 의심", unreadable);
        }
        int expected = response.path("body").path("totalCount").asInt();
        if (expected > 0 && items.size() < expected) {
            // numOfRows 가 모자라면 뒷 날짜가 통째로 잘린다. 조용히 짧은 예보를 주지 않게 남긴다.
            log.warn("관광기후지수가 잘렸습니다 — 받은 {}건 / 전체 {}건. numOfRows 를 늘려야 합니다",
                    items.size(), expected);
        }
        return byDate;
    }

    /** 항목의 대상 날짜. {@code tm} 은 {@code "2026-08-04 00:00"} 형식이라 앞 열 글자가 날짜다. */
    private static LocalDate dateOf(JsonNode item) {
        String tm = item.path("tm").asText(null);
        if (tm == null || tm.length() < YMD_DASHED_LENGTH) {
            return null;
        }
        try {
            return LocalDate.parse(tm.substring(0, YMD_DASHED_LENGTH));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
