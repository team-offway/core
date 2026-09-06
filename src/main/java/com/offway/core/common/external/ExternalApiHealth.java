package com.offway.core.common.external;

import com.offway.core.common.notification.Notifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 외부 시스템이 지금 살아 있는가(#474) — <b>이미 하고 있는 호출의 결과로</b> 판정한다.
 *
 * <p><b>왜 필요했나.</b> 2026-09-06 {@code apis.data.go.kr} 이 통째로 죽었는데, 우리가 그 사실을 안 것은
 * 사람이 "왜 안 되지" 하고 물어봤을 때다. 요청마다 개별적으로 실패하고 서로 그 사실을 모른다. 로그에는
 * 남지만 아무도 안 보고 있으면 없는 것과 같다.
 *
 * <p><b>따로 쏘지 않는다.</b> 헬스체크용 호출을 주기적으로 넣으면 그만큼 외부 한도를 태운다 — TMAP
 * 경유지최적화는 하루 50 이라 그것만으로도 아깝다. 우리는 이미 그 API 들을 부르고 있으므로, 그 결과를
 * 보는 편이 공짜이면서 <b>사용자가 실제로 겪는 것</b>과 같다.
 *
 * <h2>알림은 상태가 바뀔 때만</h2>
 *
 * <p>매 실패마다 보내면 장애 한 번에 채널이 수백 줄로 덮여 아무도 안 본다. 정상 → 장애, 장애 → 회복
 * 두 순간만 알린다.
 *
 * <h2>한 번 실패로 장애라 하지 않는다</h2>
 *
 * <p>외부는 원래 가끔 실패한다. 연속 {@value #FAILURES_TO_OPEN} 번이어야 장애로 본다 — 그보다 낮으면
 * 평상시에도 알림이 울려 신호가 죽는다. 회복은 한 번이면 충분하다(성공했다는 것이 곧 증거다).
 *
 * <h2>판정만 하고 막지는 않는다</h2>
 *
 * <p>서킷 브레이커가 아니다. "죽었으니 부르지 말자" 는 별개 결정이고, 그러려면 폴백이 모든 경로에
 * 있어야 한다. 여기서는 <b>우리가 먼저 아는 것</b>까지만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiHealth {

    /** 이만큼 연속 실패해야 장애로 본다. */
    private static final int FAILURES_TO_OPEN = 3;

    /**
     * 같은 시스템의 장애 알림을 다시 보내기까지의 간격.
     *
     * <p>상태가 바뀔 때만 보내므로 보통은 한 번뿐이다. 다만 장애가 길어지면 잊히므로 이 간격마다
     * 한 번 더 알린다 — "아직 죽어 있다" 는 것도 정보다.
     */
    private static final Duration REMIND_AFTER = Duration.ofHours(1);

    private final Notifier notifier;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    /** 호출이 성공했다. */
    public void succeeded(String system) {
        State previous = states.put(system, State.healthy());
        if (previous != null && previous.down()) {
            log.info("외부 시스템 회복 — {} (연속 실패 {}회 뒤)", system, previous.failures());
            notify("회복", system, "다시 응답합니다");
        }
    }

    /** 호출이 실패했다 — 응답이 없거나 5xx 다. */
    public void failed(String system, String cause) {
        State updated = states.compute(system, (key, previous) ->
                previous == null ? State.firstFailure() : previous.andFailed());
        if (updated.failures() < FAILURES_TO_OPEN) {
            return; // 한 번씩 실패하는 것은 평상시다
        }
        if (updated.alreadyNotified() && !updated.remindDue()) {
            return;
        }
        // put 이 아니라 CAS 다 — 사이에 성공이 끼어들었다면 그 회복 상태를 덮지 않고 알림만 포기한다.
        // 외부 호출은 병렬로 나가므로 이 창은 실제로 열린다.
        if (!states.replace(system, updated, updated.notified())) {
            return;
        }
        log.warn("외부 시스템 장애 — {} 연속 실패 {}회 cause={}", system, updated.failures(), cause);
        notify("장애", system, "연속 %d회 실패 · %s".formatted(updated.failures(), cause));
    }

    /** 지금 살아 있다고 보는가 — 조회용이다. 아직 아무도 안 부른 시스템은 살아 있는 것으로 본다. */
    public boolean isHealthy(String system) {
        State state = states.get(system);
        return state == null || !state.down();
    }

    private void notify(String kind, String system, String detail) {
        try {
            notifier.send("외부 %s — `%s` %s".formatted(kind, system, detail));
        } catch (RuntimeException e) {
            // 관측이 기능을 막으면 안 된다 — 알림이 터져도 호출 경로는 그대로 간다(#421 과 같은 규칙).
            log.warn("외부 상태 알림 실패 system={} cause={}", system, e.getClass().getSimpleName());
        }
    }

    /**
     * 한 시스템의 상태.
     *
     * @param failures 연속 실패 수. 성공하면 0 으로 돌아간다
     * @param notifiedAt 장애를 알린 시각. 안 알렸으면 null
     */
    private record State(int failures, Instant notifiedAt) {

        static State healthy() {
            return new State(0, null);
        }

        static State firstFailure() {
            return new State(1, null);
        }

        State andFailed() {
            return new State(failures + 1, notifiedAt);
        }

        State notified() {
            return new State(failures, Instant.now());
        }

        boolean down() {
            return failures >= FAILURES_TO_OPEN;
        }

        boolean alreadyNotified() {
            return notifiedAt != null;
        }

        boolean remindDue() {
            return notifiedAt != null && notifiedAt.isBefore(Instant.now().minus(REMIND_AFTER));
        }
    }
}
