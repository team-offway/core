package com.offway.core.liveactivity.infrastructure.apns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * APNs 로 나가는 본문의 <b>칸 이름과 모양</b>(#575, #577 리뷰).
 *
 * <p><b>여기가 이 기능에서 가장 조용히 깨지는 자리다.</b> {@code content-state} 의 칸 이름이 앱의
 * {@code TripActivityAttributes.ContentState} 와 하나라도 어긋나면 iOS 가 디코딩에 실패하는데,
 * 오류를 주지 않고 <b>화면만 안 바뀐다</b> — 로그에도 흔적이 없다. stub 으로 발송을 가로채는 테스트는
 * 이 변환을 지나지 않으므로 그 사고를 못 잡는다.
 */
class ApnsPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Instant NOW = Instant.ofEpochSecond(1_789_344_000L);

    private static final LocalDate START = LocalDate.of(2026, 9, 23);

    private static final LocalDate END = LocalDate.of(2026, 9, 25);

    @Test
    void 갱신의_칸_이름은_앱의_ContentState_와_같다() throws Exception {
        JsonNode aps = parse(new LiveActivityPush.Update("정선군", 2, null, START, END)).get("aps");

        assertEquals("update", aps.get("event").asText());
        JsonNode state = aps.get("content-state");
        assertEquals("정선군", state.get("regionName").asText());
        assertEquals(2, state.get("daysLeft").asInt());
        assertEquals("2026-09-23", state.get("startDate").asText());
        assertEquals("2026-09-25", state.get("endDate").asText());
    }

    /**
     * <b>{@code null} 인 칸을 빼지 않는다.</b>
     *
     * <p>{@code daysLeft} 와 {@code dayNth} 는 둘 중 하나가 비어 있다는 것 자체가 뜻이다. 빼 버리면
     * 앱이 직전 값을 그대로 쓰므로, 여행을 떠난 날 잠금화면에 어제의 {@code D-1} 이 남는다.
     */
    @Test
    void 비어_있는_칸도_실어_보낸다() throws Exception {
        JsonNode state = parse(new LiveActivityPush.Update("정선군", 2, null, START, END))
                .get("aps")
                .get("content-state");

        assertTrue(state.has("dayNth"), "빈 칸을 빼면 앱이 직전 값을 그대로 쓴다");
        assertTrue(state.get("dayNth").isNull());
    }

    @Test
    void 여행_중에는_며칠째만_값이_있다() throws Exception {
        JsonNode state = parse(new LiveActivityPush.Update("정선군", null, 2, START, END))
                .get("aps")
                .get("content-state");

        assertTrue(state.get("daysLeft").isNull());
        assertEquals(2, state.get("dayNth").asInt());
    }

    /**
     * {@code timestamp} 는 <b>초 단위</b> Unix time 이다.
     *
     * <p>밀리초로 넣으면 값이 1000배가 되는데, iOS 는 그것을 조용히 받아들이고 <b>다음 갱신이 더 작은
     * 값으로 보여 전부 무시된다</b> — 그날 이후 잠금화면이 영영 안 바뀐다.
     */
    @Test
    void timestamp_는_초_단위다() throws Exception {
        JsonNode aps = parse(new LiveActivityPush.Update("정선군", 2, null, START, END)).get("aps");

        assertEquals(1_789_344_000L, aps.get("timestamp").asLong());
    }

    @Test
    void 종료는_지금_치우라고_함께_보낸다() throws Exception {
        JsonNode aps = parse(LiveActivityPush.END).get("aps");

        assertEquals("end", aps.get("event").asText());
        assertEquals(1_789_344_000L, aps.get("dismissal-date").asLong());
        assertTrue(aps.get("content-state") == null, "종료에 상태를 실을 이유가 없다");
    }

    /** 갱신은 자는 사람을 깨우지 않고(5), 종료는 지금 치운다(10). */
    @Test
    void 갱신과_종료의_우선순위가_다르다() {
        assertEquals("5", ApnsPayload.priorityOf(new LiveActivityPush.Update("정선군", 2, null, START, END)));
        assertEquals("10", ApnsPayload.priorityOf(LiveActivityPush.END));
    }

    /** 지역 이름을 못 찾아도 본문은 만들어진다 — 이름은 곁가지다. */
    @Test
    void 지역_이름이_없어도_본문을_만든다() throws Exception {
        JsonNode state = parse(new LiveActivityPush.Update(null, 2, null, START, END))
                .get("aps")
                .get("content-state");

        assertTrue(state.get("regionName").isNull());
        assertEquals(2, state.get("daysLeft").asInt());
    }

    private static JsonNode parse(LiveActivityPush push) throws Exception {
        return MAPPER.readTree(ApnsPayload.body(push, NOW));
    }
}
