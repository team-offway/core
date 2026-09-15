package com.offway.core.liveactivity.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivityPush;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 대상 하나가 실어 보낼 내용으로 바뀌는 규칙(#575).
 *
 * <p>여기서 갈리는 것은 <b>갱신이냐 종료냐</b> 하나다. 끝난 여행에 갱신을 보내면 끝난 D-day 가 잠금화면에
 * 그대로 남고, 그건 이 기능이 고치려던 증상과 같다.
 */
class LiveActivityTargetTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 23);

    private static final LocalDate END = LocalDate.of(2026, 9, 25);

    @Test
    void 출발_전이면_남은_날을_실어_보낸다() {
        LiveActivityTarget target = target(TripProgress.of(START, 3, LocalDate.of(2026, 9, 21)));

        LiveActivityPush.Update push = assertInstanceOf(LiveActivityPush.Update.class, target.toPush());
        assertEquals("정선군", push.regionName());
        assertEquals(2, push.daysLeft());
        assertNull(push.dayNth());
        assertEquals(START, push.startDate());
        assertEquals(END, push.endDate());
        assertFalse(target.doneAfterSend());
    }

    @Test
    void 여행_중이면_며칠째인지_실어_보낸다() {
        LiveActivityTarget target = target(TripProgress.of(START, 3, LocalDate.of(2026, 9, 24)));

        LiveActivityPush.Update push = assertInstanceOf(LiveActivityPush.Update.class, target.toPush());
        assertNull(push.daysLeft());
        assertEquals(2, push.dayNth());
    }

    /** 끝났으면 갱신이 아니라 종료다 — 그리고 보내고 나면 등록도 지운다. */
    @Test
    void 끝났으면_종료를_보내고_등록을_지운다() {
        LiveActivityTarget target = target(TripProgress.of(START, 3, LocalDate.of(2026, 9, 26)));

        assertInstanceOf(LiveActivityPush.End.class, target.toPush());
        assertTrue(target.doneAfterSend(), "끝난 등록을 남기면 내일도 같은 종료를 또 보낸다");
    }

    /** 지역 이름을 못 찾아도 갱신은 나간다 — 이름은 곁가지다. */
    @Test
    void 지역_이름이_없어도_갱신은_나간다() {
        LiveActivityTarget target = LiveActivityTarget.builder()
                .rowId(1L)
                .token("token")
                .courseId(7L)
                .progress(TripProgress.of(START, 3, LocalDate.of(2026, 9, 21)))
                .regionName(null)
                .startDate(START)
                .endDate(END)
                .build();

        LiveActivityPush.Update push = assertInstanceOf(LiveActivityPush.Update.class, target.toPush());
        assertNull(push.regionName());
        assertEquals(2, push.daysLeft());
    }

    private static LiveActivityTarget target(TripProgress progress) {
        return LiveActivityTarget.builder()
                .rowId(1L)
                .token("token")
                .courseId(7L)
                .progress(progress)
                .regionName("정선군")
                .startDate(START)
                .endDate(END)
                .build();
    }
}
