package com.offway.core.liveactivity.infrastructure.apns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * APNs 를 직접 부르는 adapter(#575).
 *
 * <h2>왜 직접 부르나</h2>
 *
 * <p><b>FCM 이 Live Activity 를 중계하지 않는다.</b> 기존 알림이 전부 FCM 으로 나가고 있어도 이 한
 * 종류만은 별도 경로가 필요하다. 이 작업의 대부분이 그 경로를 세우는 일이다.
 *
 * <h2>HTTP/2 라 JDK 클라이언트를 쓴다</h2>
 *
 * <p>APNs 는 HTTP/2 만 받는다. {@code java.net.http.HttpClient} 는 HTTP/2 를 기본으로 협상하고 연결을
 * 재사용하므로 새 의존성이 필요 없다 — WebClient(Reactor Netty)로 가면 HTTP/2 를 따로 켜야 하고,
 * 이 한 곳 때문에 그 설정을 전역에 들이게 된다.
 *
 * <h2>조용히 실패하는 지점이 둘 있다</h2>
 *
 * <p>둘 다 오류를 주지 않아 <b>로그에도 흔적이 안 남는</b> 종류다.
 *
 * <ul>
 *   <li>{@code apns-topic} 이 틀리면 — 앱 번들 ID 에 {@code .push-type.liveactivity} 를 붙인 값이어야
 *       한다. 확장 번들 ID 가 아니다
 *   <li>{@code content-state} 의 필드 이름이 앱과 어긋나면 — iOS 가 디코딩에 실패하고 화면만 안 바뀐다
 * </ul>
 *
 * <p>그래서 보낸 건수만 세지 않고 <b>무엇을 어느 환경으로 보냈는지</b>를 함께 남긴다.
 */
@Slf4j
@Component
public class ApnsLiveActivitySender implements LiveActivitySender {

    /** Live Activity 전용 푸시 타입. 이 값이 아니면 APNs 가 받지 않는다. */
    private static final String PUSH_TYPE = "liveactivity";

    /**
     * 갱신의 우선순위 — 5(전력 절약).
     *
     * <p>자정에 도는 하루치 갱신이라 즉시 깨울 이유가 없다. 10 으로 올리면 기기를 깨워 배터리를 쓰는데,
     * 사용자가 그 차이로 얻는 것이 없다.
     */
    private static final String PRIORITY_UPDATE = "5";

    /**
     * 종료의 우선순위 — 10.
     *
     * <p>이쪽은 <b>지금 치워야</b> 한다. 늦어지면 끝난 여행이 잠금화면에 남아 있는 시간이 그만큼 길어진다.
     */
    private static final String PRIORITY_END = "10";

    /**
     * 기기가 꺼져 있을 때 APNs 가 들고 있어 줄 시간.
     *
     * <p><b>24시간을 주지 않는다.</b> 이 값은 <b>오늘의</b> D-day 라, 자정 발송 기준으로 하루를 주면
     * 다음 자정을 넘겨 <b>어제 값이 닿는다</b>. 그러면 이 기능이 고치려던 바로 그 증상이 재현된다.
     */
    private static final Duration DELIVERY_WINDOW = Duration.ofHours(12);

    /**
     * 연결·응답 상한.
     *
     * <p><b>실측값이 아니다.</b> 이 레포는 timeout 을 p99 에서 정하는데(CLAUDE.md), APNs 는 이번에 처음
     * 붙는 경로라 잴 표본이 없다. 우선 보수적으로 두고 <b>첫 운영 회차의 분포를 재서 고친다</b> —
     * 재기 전까지 이 숫자는 근거가 아니라 자리표시라는 것을 적어 둔다.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** APNs 오류 본문의 사유 칸. */
    private static final String REASON_FIELD = "reason";

    /** 형식이 틀렸거나 <b>다른 환경의</b> 토큰 — 살려 둘 이유가 없다. */
    private static final String REASON_BAD_DEVICE_TOKEN = "BadDeviceToken";

    /** 앱이 지워졌다 — 죽은 토큰이다. */
    private static final String REASON_UNREGISTERED = "Unregistered";

    private static final int STATUS_OK = 200;

    private static final int STATUS_BAD_REQUEST = 400;

    private static final int STATUS_TOO_MANY_REQUESTS = 429;

    private static final int STATUS_GONE = 410;

    private static final int STATUS_SERVER_ERROR = 500;

    private final ApnsProperties properties;

    /**
     * 이 어댑터만 쓰는 매퍼.
     *
     * <p>컨텍스트의 빈을 주입받지 않는다 — 다른 외부 API 어댑터들도 저마다 하나씩 들고 있고(TAGO·TMAP·
     * 기상청), 그쪽 설정(응답 관용도·날짜 형식)이 우리 요청 본문의 모양을 바꾸면 안 된다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient;

    /** 키가 없으면 만들지 않는다 — 부팅은 되고 발송만 비활성이다. */
    private final ApnsProviderToken providerToken;

    public ApnsLiveActivitySender(ApnsProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.providerToken = providerToken(properties);
    }

    @Override
    public ApnsResult send(String token, LiveActivityPush push) {
        if (providerToken == null) {
            return ApnsResult.DISABLED;
        }
        Instant now = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.pushUrl(token)))
                    .timeout(REQUEST_TIMEOUT)
                    .header("authorization", "bearer " + providerToken.value())
                    .header("apns-push-type", PUSH_TYPE)
                    .header("apns-topic", properties.topic())
                    .header("apns-priority", priorityOf(push))
                    .header("apns-expiration", String.valueOf(now.plus(DELIVERY_WINDOW).getEpochSecond()))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(push, now)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return interpret(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApnsResult.FAILED;
        } catch (Exception e) {
            // 한 건의 실패가 나머지를 막으면 안 된다(port 계약). 다만 조용히 넘기지도 않는다 —
            // 토큰은 싣지 않는다(그 값을 아는 쪽은 남의 잠금화면에 내용을 그릴 수 있다).
            log.warn("Live Activity 발송 실패 env={} cause={}",
                    properties.environmentName(), e.getClass().getSimpleName());
            return ApnsResult.FAILED;
        }
    }

    /**
     * 응답을 대응으로 옮긴다 — <b>토큰을 지울 것인가</b>가 갈리는 자리다.
     *
     * <p>{@code 400} 을 한 덩어리로 보지 않는다. {@code BadDeviceToken} 은 그 토큰이 틀린 것이지만,
     * 나머지 {@code 400}(토픽 오류·본문 오류)은 <b>우리 설정이 틀린 것</b>이라 토큰을 지우면 멀쩡한
     * 등록이 매일 조금씩 사라진다.
     */
    private ApnsResult interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == STATUS_OK) {
            return ApnsResult.SENT;
        }
        String reason = reasonOf(response.body());
        if (status == STATUS_GONE
                || (status == STATUS_BAD_REQUEST
                        && (REASON_BAD_DEVICE_TOKEN.equals(reason) || REASON_UNREGISTERED.equals(reason)))) {
            log.debug("Live Activity 토큰이 죽었습니다 status={} reason={}", status, reason);
            return ApnsResult.GONE;
        }
        if (status == STATUS_TOO_MANY_REQUESTS) {
            log.warn("APNs 가 속도를 제한했습니다 — 이번 회차를 여기서 멈춥니다 reason={}", reason);
            return ApnsResult.THROTTLED;
        }
        if (status < STATUS_SERVER_ERROR) {
            // 설정 문제는 재시도가 풀어주지 않는다. 사유를 남겨야 topic·payload 중 어느 쪽인지 가른다.
            log.warn("Live Activity 발송이 거절됐습니다 — 설정을 확인하세요 status={} reason={} env={} topic={}",
                    status, reason, properties.environmentName(), properties.topic());
        } else {
            log.warn("APNs 가 오류를 냈습니다 status={} reason={}", status, reason);
        }
        return ApnsResult.FAILED;
    }

    /** 오류 본문에서 사유만 꺼낸다. 본문이 깨져 있어도 발송 흐름을 막지 않는다. */
    private String reasonOf(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode reason = objectMapper.readTree(body).get(REASON_FIELD);
            return reason == null ? "" : reason.asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 실어 보낼 JSON.
     *
     * <p>{@code timestamp} 는 <b>초 단위</b> Unix time 이다. 이 값이 직전 것보다 작으면 iOS 가 조용히
     * 무시하므로, 보낼 때마다 현재 시각으로 새로 넣는다.
     */
    private String body(LiveActivityPush push, Instant now) throws Exception {
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("timestamp", now.getEpochSecond());
        switch (push) {
            case LiveActivityPush.Update update -> {
                aps.put("event", "update");
                aps.put("content-state", contentState(update));
            }
            case LiveActivityPush.End ignored -> {
                aps.put("event", "end");
                // 지금 치운다. 이 값을 빼면 iOS 가 스스로 걷어낼 때까지 최대 12시간 남는다.
                aps.put("dismissal-date", now.getEpochSecond());
            }
        }
        return objectMapper.writeValueAsString(Map.of("aps", aps));
    }

    /**
     * 앱의 {@code TripActivityAttributes.ContentState} 와 <b>1:1</b> 인 칸들.
     *
     * <p>이름이 하나라도 어긋나면 갱신이 통째로 실패하는데 <b>오류가 오지 않는다</b>. 여기 문자열을
     * 고칠 일이 생기면 앱 쪽과 같은 PR 에서 함께 고쳐야 한다.
     *
     * <p>{@code null} 인 칸도 실어 보낸다 — {@code daysLeft}·{@code dayNth} 는 <b>둘 중 하나가 비어
     * 있다는 것 자체가 뜻</b>(출발 전이냐 여행 중이냐)이라, 빼 버리면 앱이 직전 값을 그대로 쓴다.
     */
    private Map<String, Object> contentState(LiveActivityPush.Update update) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("regionName", update.regionName());
        state.put("daysLeft", update.daysLeft());
        state.put("dayNth", update.dayNth());
        state.put("startDate", format(update.startDate()));
        state.put("endDate", format(update.endDate()));
        return state;
    }

    private static String format(LocalDate date) {
        return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String priorityOf(LiveActivityPush push) {
        return switch (push) {
            case LiveActivityPush.Update ignored -> PRIORITY_UPDATE;
            case LiveActivityPush.End ignored -> PRIORITY_END;
        };
    }

    /**
     * 설정이 없으면 {@code null} — 부팅을 막지 않는다.
     *
     * <p>이 레포의 불변식은 "local 프로파일에서 시크릿 없이 부팅 가능" 이다(CLAUDE.md). 키를 부팅
     * 조건으로 만들면 로컬 개발과 CI 스모크가 전부 막힌다.
     */
    private static ApnsProviderToken providerToken(ApnsProperties properties) {
        if (!properties.configured()) {
            log.warn("APNs 설정이 없습니다 — 잠금화면 갱신 없이 뜹니다"
                    + " (offway.apns.team-id · key-id · private-key-base64 · topic)");
            return null;
        }
        try {
            return new ApnsProviderToken(properties);
        } catch (RuntimeException e) {
            // 키가 깨졌다고 서버를 못 뜨게 하지 않는다. 발송만 죽고 나머지는 정상이어야 한다.
            log.error("APNs 초기화에 실패해 잠금화면 갱신 없이 뜹니다", e);
            return null;
        }
    }
}
