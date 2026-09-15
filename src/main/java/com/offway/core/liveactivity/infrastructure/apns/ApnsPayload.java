package com.offway.core.liveactivity.infrastructure.apns;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * APNs 로 나가는 본문과 우선순위(#575).
 *
 * <h2>왜 어댑터에서 떼어 냈나</h2>
 *
 * <p>여기 있는 문자열들이 <b>이 기능에서 가장 조용히 깨지는 것</b>이다. {@code content-state} 의 칸
 * 이름이 앱의 {@code TripActivityAttributes.ContentState} 와 하나라도 어긋나면 iOS 가 디코딩에
 * 실패하는데, <b>오류를 주지 않고 화면만 안 바뀐다</b> — 로그에도 흔적이 없다.
 *
 * <p>어댑터 안에 두면 이 계약을 확인하려면 HTTP 를 태워야 하고, 그러려면 어댑터에 클라이언트 주입
 * 구멍을 내야 한다. 본문 조립은 HTTP 와 무관한 순수 변환이라 떼어 내면 <b>그 자체로 단위 테스트가
 * 된다</b>(#577 리뷰).
 */
final class ApnsPayload {

    /**
     * 갱신의 우선순위 — 5(전력 절약).
     *
     * <p>자정에 도는 하루치 갱신이라 즉시 깨울 이유가 없다. 10 으로 올리면 기기를 깨워 배터리를 쓰는데,
     * 사용자가 그 차이로 얻는 것이 없다.
     */
    static final String PRIORITY_UPDATE = "5";

    /**
     * 종료의 우선순위 — 10.
     *
     * <p>이쪽은 <b>지금 치워야</b> 한다. 늦어지면 끝난 여행이 잠금화면에 남아 있는 시간이 그만큼 길어진다.
     */
    static final String PRIORITY_END = "10";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApnsPayload() {
    }

    /**
     * 실어 보낼 JSON.
     *
     * <p>{@code timestamp} 는 <b>초 단위</b> Unix time 이다. 이 값이 직전 것보다 작으면 iOS 가 조용히
     * 무시하므로, 보낼 때마다 현재 시각으로 새로 넣는다.
     */
    static String body(LiveActivityPush push, Instant now) throws JsonProcessingException {
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
        return OBJECT_MAPPER.writeValueAsString(Map.of("aps", aps));
    }

    static String priorityOf(LiveActivityPush push) {
        return switch (push) {
            case LiveActivityPush.Update ignored -> PRIORITY_UPDATE;
            case LiveActivityPush.End ignored -> PRIORITY_END;
        };
    }

    /**
     * 앱의 {@code TripActivityAttributes.ContentState} 와 <b>1:1</b> 인 칸들.
     *
     * <p>이름이 하나라도 어긋나면 갱신이 통째로 실패하는데 <b>오류가 오지 않는다</b>. 여기 문자열을
     * 고칠 일이 생기면 앱 쪽과 같은 PR 에서 함께 고쳐야 한다.
     *
     * <p><b>{@code null} 인 칸도 실어 보낸다.</b> {@code daysLeft}·{@code dayNth} 는 둘 중 하나가 비어
     * 있다는 것 자체가 뜻(출발 전이냐 여행 중이냐)이라, 빼 버리면 앱이 직전 값을 그대로 쓴다 — 여행을
     * 떠난 날 잠금화면에 어제의 {@code D-1} 이 남는다.
     */
    private static Map<String, Object> contentState(LiveActivityPush.Update update) {
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
}
