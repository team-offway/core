package com.offway.core.weather.infrastructure.kma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.weather.domain.MidLandRegion;
import com.offway.core.weather.domain.MidTermOutlook;
import com.offway.core.weather.domain.MidTermOutlook.DayOutlook;
import com.offway.core.weather.domain.SkyState;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기상청 중기 육상예보 adapter — {@code MidFcstInfoService/getMidLandFcst}(#129). 단기예보와 같은 serviceKey 를 쓴다.
 *
 * <p><b>응답 모양이 단기예보와 다르다.</b> 단기는 항목마다 날짜·카테고리가 붙은 목록인데, 중기는 <b>한 행에 D+4 부터 D+10
 * 까지가 컬럼으로</b> 들어온다({@code wf4Am}·{@code wf5Am}···{@code wf10}). 게다가 D+7 까지만 오전·오후가 나뉘고 D+8
 * 부터는 하루 하나다.
 *
 * <p>발표는 하루 두 번(06·18시)이라 조회 파라미터 {@code tmFc} 를 그 슬롯으로 맞춘다. 아직 안 나온 슬롯을 요청하면 빈
 * 응답이 오므로 지연을 얹어 직전 슬롯을 쓴다.
 *
 * <p><b>기온은 이 어댑터가 다루지 않는다.</b> 중기 기온은 {@code getMidTa} 라는 다른 오퍼레이션에 <b>시군 단위 지점
 * 코드</b>로 조회하는데, 여기 쓰는 광역 구역 코드를 넣으면 오류가 아니라 <b>0℃가 조용히</b> 온다(실측 확인). 지점 코드를
 * 확보하기 전까지 기온은 비워 둔다.
 */
@Slf4j
@Component
class MidTermForecastClientImpl implements MidTermForecastClient {

    private static final String URL = "https://apis.data.go.kr/1360000/MidFcstInfoService/getMidLandFcst";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    /** 구역 하나당 한 행이라 넉넉히 잡을 이유가 없다. */
    private static final int ROWS = 10;

    /** 발표 슬롯(KST). 하루 두 번이다. */
    private static final int MORNING_RELEASE_HOUR = 6;
    private static final int EVENING_RELEASE_HOUR = 18;

    /** 발표 후 제공까지 지연 — 정각에 요청하면 아직 안 올라와 빈 응답이 온다. */
    private static final int PUBLISH_DELAY_MIN = 10;

    private static final ZoneId KMA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TM_FC = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final WebClient webClient;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    MidTermForecastClientImpl(WebClient externalWebClient, ExternalApiProperties props) {
        this.webClient = externalWebClient;
        this.props = props;
    }

    @Override
    public Optional<MidTermOutlook> outlook(MidLandRegion region) {
        if (!props.dataGoKr().hasKey()) {
            return Optional.empty();
        }
        LocalDateTime release = latestRelease(LocalDateTime.now(KMA_ZONE));
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("serviceKey", props.dataGoKr().serviceKey())
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", ROWS)
                .queryParam("pageNo", 1)
                .queryParam("regId", region.regId())
                .queryParam("tmFc", release.format(TM_FC));
        try {
            return parse(call(builder), release.toLocalDate());
        } catch (Exception e) {
            log.warn("기상청 중기예보 조회 실패 — 날씨 생략 regId={} cause={}",
                    region.regId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String call(UriComponentsBuilder builder) {
        // serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다(build(true)) — 다른 data.go.kr 어댑터와 같은 규약.
        URI uri = builder.build(true).toUri();
        return webClient.get().uri(uri).retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
    }

    /** 아직 안 나온 슬롯을 요청하지 않도록 지연을 뺀 직전 발표 시각. 06시 이전이면 전날 18시다. */
    static LocalDateTime latestRelease(LocalDateTime now) {
        LocalDateTime t = now.minusMinutes(PUBLISH_DELAY_MIN);
        if (t.getHour() >= EVENING_RELEASE_HOUR) {
            return t.toLocalDate().atTime(EVENING_RELEASE_HOUR, 0);
        }
        if (t.getHour() >= MORNING_RELEASE_HOUR) {
            return t.toLocalDate().atTime(MORNING_RELEASE_HOUR, 0);
        }
        return t.toLocalDate().minusDays(1).atTime(EVENING_RELEASE_HOUR, 0);
    }

    private Optional<MidTermOutlook> parse(String body, LocalDate baseDate) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");
        if (!"00".equals(response.path("header").path("resultCode").asText())) {
            return Optional.empty();
        }
        JsonNode item = response.path("body").path("items").path("item");
        JsonNode row = item.isArray() ? item.path(0) : item;
        if (row.isMissingNode() || row.isEmpty()) {
            // resultCode 는 성공인데 결과가 비어 오는 흔한 경우. 조용히 넘기면 흔적이 안 남는다.
            log.warn("기상청 중기예보 응답이 비어 왔습니다 — 날씨 생략");
            return Optional.empty();
        }

        Map<LocalDate, DayOutlook> byDate = new HashMap<>();
        for (int day = MidTermOutlook.FIRST_DAY; day <= MidTermOutlook.LAST_DAY; day++) {
            Optional<DayOutlook> outlook = dayOutlook(row, day);
            if (outlook.isPresent()) {
                byDate.put(baseDate.plusDays(day), outlook.get());
            }
        }
        if (byDate.isEmpty()) {
            log.warn("기상청 중기예보에서 쓸 수 있는 날짜가 없습니다 — 스키마 변경 의심");
            return Optional.empty();
        }
        return Optional.of(new MidTermOutlook(baseDate, byDate));
    }

    /**
     * 하루치를 뽑는다. D+7 까지는 오전·오후가 나뉘어 있어 <b>둘을 합친다</b> — 하늘은 나쁜 쪽, 강수확률은 큰 쪽을 쓴다.
     * "오후에 비" 를 "비 안 옴" 으로 뭉개면 여행 계획이 틀어진다.
     */
    private static Optional<DayOutlook> dayOutlook(JsonNode row, int day) {
        boolean split = day <= 7;
        String skyText = split ? text(row, "wf" + day + "Am") : text(row, "wf" + day);
        String skyPm = split ? text(row, "wf" + day + "Pm") : null;
        Integer rainAm = intOrNull(row, split ? "rnSt" + day + "Am" : "rnSt" + day);
        Integer rainPm = split ? intOrNull(row, "rnSt" + day + "Pm") : null;

        SkyState sky = SkyState.fromMidTermText(skyText).worseOf(SkyState.fromMidTermText(skyPm));
        Integer rain = maxOf(rainAm, rainPm);
        if (sky == SkyState.UNKNOWN && rain == null) {
            return Optional.empty();
        }
        return Optional.of(new DayOutlook(sky, rain));
    }

    private static String text(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static Integer intOrNull(JsonNode row, String field) {
        JsonNode node = row.path(field);
        return node.isNumber() ? node.asInt() : null;
    }

    private static Integer maxOf(Integer current, Integer candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null ? candidate : Math.max(current, candidate);
    }
}
