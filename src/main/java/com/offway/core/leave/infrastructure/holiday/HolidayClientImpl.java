package com.offway.core.leave.infrastructure.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offway.core.common.config.ExternalApiProperties;
import com.offway.core.common.logging.RootCause;
import com.offway.core.leave.domain.HolidayException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.ExternalApiCallRecorder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 특일정보(공휴일·대체공휴일) adapter — 한국천문연구원 getRestDeInfo.
 *
 * <p>키가 없으면 외부 호출 없이 빈 집합을 돌려준다(로컬 실행성 불변식). 호출·파싱 실패는 {@link HolidayException}(502)으로
 * 올린다.
 */
@Slf4j
@Component
class HolidayClientImpl implements HolidayClient {

    private static final String BASE =
            "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";
    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String HOLIDAY_FLAG = "Y";
    private static final String SUCCESS_CODE = "00";
    private static final int MAX_ROWS = 100;

    private final WebClient webClient;
    private final ExternalApiCallRecorder callRecorder;
    private final ExternalApiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    HolidayClientImpl(WebClient externalWebClient, ExternalApiProperties props,
            ExternalApiCallRecorder callRecorder) {
        this.webClient = externalWebClient;
        this.props = props;
        this.callRecorder = callRecorder;
    }

    @Override
    public Set<LocalDate> getHolidays(int solYear, int solMonth) {
        if (!props.dataGoKr().hasKey()) {
            log.info("특일정보 키 없음 — 공휴일 조회를 건너뜁니다 (year={} month={})", solYear, solMonth);
            return Set.of();
        }
        try {
            // 실호출 직전에 센다. 응답이 실패해도 한도는 이미 깎였다(#123).
            callRecorder.record(ExternalApi.HOLIDAY);
            String body = webClient.get()
                    .uri(uri(props.dataGoKr().serviceKey(), solYear, solMonth))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            return parse(body);
        } catch (Exception e) {
            // 쿼리스트링(키 포함)은 로그에 남기지 않는다. 사유·연월만.
            log.warn("특일정보 조회 실패 year={} month={} cause={}", solYear, solMonth, RootCause.of(e));
            throw HolidayException.lookupFailed(e);
        }
    }

    /** serviceKey 를 넣어 최종 호출 URI 를 만든다. serviceKey 는 이미 인코딩된 값이라 다시 인코딩하지 않는다. */
    private java.net.URI uri(String serviceKey, int solYear, int solMonth) {
        return UriComponentsBuilder.fromUriString(BASE)
                .queryParam("serviceKey", serviceKey)
                .queryParam("solYear", solYear)
                .queryParam("solMonth", String.format("%02d", solMonth))
                .queryParam("_type", "json")
                .queryParam("numOfRows", MAX_ROWS)
                .build(true)
                .toUri();
    }

    /**
     * 응답 JSON 에서 공휴일 날짜를 뽑는다.
     *
     * <p>data.go.kr 함정 방어: {@code items} 가 빈 문자열이거나(데이터 없음), {@code item} 이 단일 객체(1건일 때)일 수 있어
     * 트리로 순회한다. {@code isHoliday="Y"} 만 취한다.
     */
    private Set<LocalDate> parse(String body) throws Exception {
        JsonNode response = objectMapper.readTree(body).path("response");

        // 성공 코드가 아니면 빈결과로 두지 않는다. items 가 없는 실패 응답(키·쿼터·파라미터 오류나
        // 게이트웨이 XML 오류)이 "공휴일 없음" 으로 둔갑하면 연차 계산이 조용히 틀린다.
        String resultCode = response.path("header").path("resultCode").asText();
        if (!SUCCESS_CODE.equals(resultCode)) {
            throw new IllegalStateException("특일정보 응답이 성공이 아닙니다: resultCode=" + resultCode);
        }

        Set<LocalDate> holidays = new LinkedHashSet<>();
        JsonNode item = response.path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull()) {
            return holidays;
        }
        for (JsonNode node : item.isArray() ? item : objectMapper.createArrayNode().add(item)) {
            if (!HOLIDAY_FLAG.equalsIgnoreCase(node.path("isHoliday").asText())) {
                continue;
            }
            holidays.add(LocalDate.parse(node.path("locdate").asText(), LOCDATE));
        }
        return holidays;
    }
}
