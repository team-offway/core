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
 * <h2>양쪽 다 여러 번 물어보고 정한다</h2>
 *
 * <p>외부는 원래 가끔 실패한다. 연속 {@value #FAILURES_TO_OPEN} 번이어야 장애로 본다 — 그보다 낮으면
 * 평상시에도 알림이 울려 신호가 죽는다.
 *
 * <p><b>회복도 마찬가지로 연속 {@value #SUCCESSES_TO_CLOSE} 번을 요구한다(#482).</b> 처음에는 성공 한
 * 번이면 회복으로 봤는데, 배포한 날 바로 그게 틀린 것으로 드러났다. 게이트웨이가 <b>깜빡이는</b> 중이었고
 * (연속 8회 중 0회 응답, 그 직전 한 번은 200) 운 좋게 붙은 그 한 번을 우리가 "회복" 이라고 선언했다.
 * 판정이 비대칭이면 깜빡이는 장애에서 장애 → 회복 → 장애가 반복되고, <b>잦아진 알림은 결국 안 읽힌다</b> —
 * 이 기능이 없애려던 문제가 형태만 바꿔 돌아온다.
 *
 * <p>트래픽이 있으면 성공 두 번은 곧바로 쌓인다. 아무도 안 쓰는 새벽에는 프로브 주기(30분) × 2 가 걸리는데,
 * 그 시간대에 급한 것은 "아직 죽어 있다" 쪽이라 이 지연은 받아들일 만하다.
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

    /** 장애 중에 이만큼 연속 성공해야 회복으로 본다. 한 번으로 뒤집으면 깜빡이는 장애에 끌려다닌다. */
    private static final int SUCCESSES_TO_CLOSE = 2;

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
        while (true) {
            State previous = states.get(system);
            if (previous == null) {
                if (states.putIfAbsent(system, State.healthy()) == null) {
                    return;
                }
                continue;
            }
            State updated = previous.andSucceeded();
            if (!states.replace(system, previous, updated)) {
                continue; // 그 사이 다른 호출이 상태를 바꿨다 — 최신 값으로 다시 센다
            }
            if (previous.down() && !updated.down()) {
                log.info("외부 시스템 회복 — {} (연속 성공 {}회)", system, SUCCESSES_TO_CLOSE);
                notify("회복", system, "연속 %d회 정상 응답".formatted(SUCCESSES_TO_CLOSE));
            }
            return;
        }
    }

    /** 호출이 실패했다 — 응답이 없거나 5xx 다. */
    public void failed(String system, String cause) {
        while (true) {
            State previous = states.get(system);
            State updated = (previous == null ? State.healthy() : previous).andFailed();
            boolean announce = updated.down() && (!updated.alreadyNotified() || updated.remindDue());
            State stored = announce ? updated.notified() : updated;
            if (!store(system, previous, stored)) {
                continue;
            }
            if (announce) {
                log.warn("외부 시스템 장애 — {} 연속 실패 {}회 cause={}", system, updated.failures(), cause);
                notify("장애", system, "연속 %d회 실패 · %s".formatted(updated.failures(), cause));
            }
            return;
        }
    }

    /** 지금 살아 있다고 보는가 — 조회용이다. 아직 아무도 안 부른 시스템은 살아 있는 것으로 본다. */
    public boolean isHealthy(String system) {
        State state = states.get(system);
        return state == null || !state.down();
    }

    /** 읽은 값이 그대로일 때만 쓴다 — 병렬 호출이 서로의 판정을 덮지 않게. */
    private boolean store(String system, State previous, State updated) {
        return previous == null
                ? states.putIfAbsent(system, updated) == null
                : states.replace(system, previous, updated);
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
     * <p>{@code down} 을 실패 수에서 파생하지 않고 따로 든다. 회복에 연속 성공을 요구하면 <b>장애인 채로
     * 성공이 쌓이는 구간</b>이 생기는데, 그때 실패 수만 보면 "실패 3회 = 아직 장애" 와 "회복 대기 중" 을
     * 구분할 수 없다.
     *
     * @param down 지금 장애로 보는가
     * @param failures 연속 실패 수. 성공하면 0 으로 돌아간다
     * @param successes 장애 중에 쌓인 연속 성공 수. 실패하면 0 으로 돌아간다
     * @param notifiedAt 장애를 알린 시각. 안 알렸으면 null
     */
    private record State(boolean down, int failures, int successes, Instant notifiedAt) {

        static State healthy() {
            return new State(false, 0, 0, null);
        }

        State andFailed() {
            int next = failures + 1;
            return new State(down || next >= FAILURES_TO_OPEN, next, 0, notifiedAt);
        }

        State andSucceeded() {
            if (!down) {
                return healthy();
            }
            int next = successes + 1;
            return next >= SUCCESSES_TO_CLOSE
                    ? healthy()
                    : new State(true, failures, next, notifiedAt);
        }

        State notified() {
            return new State(down, failures, successes, Instant.now());
        }

        boolean alreadyNotified() {
            return notifiedAt != null;
        }

        boolean remindDue() {
            return notifiedAt != null && notifiedAt.isBefore(Instant.now().minus(REMIND_AFTER));
        }
    }
}
