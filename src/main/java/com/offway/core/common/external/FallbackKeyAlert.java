package com.offway.core.common.external;

import com.offway.core.common.notification.Notifier;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 보조 키로 넘어간 사실을 알린다(#596).
 *
 * <h2>왜 알려야 하나</h2>
 *
 * <p>주 키가 마르면 지금까지는 <b>아무 흔적도 남지 않았다.</b> TMAP 경유지 최적화가 그 대표인데,
 * 마르면 직선거리 정렬로 떨어지면서 <b>응답은 200</b> 이다 — 화면에는 순서가 엉킨 코스가 정상처럼
 * 뜨고, 사용자도 우리도 모른다. 조용한 실패를 만들지 않는다(CLAUDE.md §조용한 실패).
 *
 * <p>보조 키가 받아 주면 사용자는 정상을 보지만, <b>우리는 알아야 한다</b> — 주 키가 말랐다는 것은
 * 그날의 남은 시간 동안 계속 보조 키로 버틴다는 뜻이고, 보조 키까지 마르면 그때는 답이 없다.
 *
 * <h2>하루 한 번만 보낸다</h2>
 *
 * <p>한도가 마르면 <b>그 뒤 모든 호출이 폴백</b>이다. 건별로 알리면 알림이 폭탄이 되어, 정작 봐야 할
 * 다른 알림이 묻힌다. 같은 API·같은 날짜·같은 사유면 한 번만 보낸다.
 *
 * <h2>기억을 메모리에 둔다</h2>
 *
 * <p>DB 에 굳히지 않는 이유는 <b>재시작하면 다시 알리는 편이 낫기</b> 때문이다. 재시작 뒤 같은 알림이
 * 또 오면 "컨테이너가 다시 떴고 여전히 주 키가 말라 있다" 를 알게 된다. 그 정보가 중복 한 줄보다 값어치
 * 있다. 키 공간도 API 개수만큼이라 유한하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackKeyAlert {

    /** 일일 한도가 KST 자정에 돌아온다 — 알림의 "하루" 도 같은 경계를 쓴다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Notifier notifier;

    /** (API · 사유) → 마지막으로 알린 날짜. */
    private final Map<String, LocalDate> notifiedOn = new ConcurrentHashMap<>();

    /**
     * 주 키가 실패해 <b>보조 키가 받아 줬다</b> — 사용자는 정상을 본다.
     *
     * @param api 어느 한도가 말랐나
     * @param cause 주 키 실패 사유. <b>키·URL 이 마스킹된 값</b>만 넘긴다({@code RootCause})
     */
    public void switchedToFallback(ExternalApi api, String cause) {
        send(api, "보조키전환",
                "🔁 %s — 주 키가 실패해 **보조 키로 넘어갔습니다**. 화면은 정상입니다.\n사유 `%s`"
                        .formatted(api.label(), cause));
    }

    /**
     * 보조 키까지 실패했다 — <b>여기서부터 화면이 조용히 나빠진다</b>.
     *
     * <p>이 알림이 뜨면 그 기능은 폴백(예: 직선거리 정렬)으로 돌고 있다. 응답은 200 이라 화면만 보고는
     * 알 수 없으므로, 이 줄이 유일한 신호다.
     */
    public void bothFailed(ExternalApi api, String cause) {
        send(api, "둘다실패",
                "🔴 %s — 주 키와 보조 키가 **모두 실패**했습니다. 이 기능은 지금 폴백으로 돕니다.\n사유 `%s`"
                        .formatted(api.label(), cause));
    }

    /**
     * 주 키가 실패했는데 <b>보조 키가 설정돼 있지 않다</b>.
     *
     * <p>설정 누락이라 배포로만 고쳐진다. 그래서 사유를 따로 가른다 — 위 둘과 대응이 다르다.
     */
    public void noFallbackConfigured(ExternalApi api, String cause) {
        send(api, "보조키없음",
                "⚠️ %s — 주 키가 실패했는데 **보조 키가 설정돼 있지 않습니다**.\n사유 `%s`"
                        .formatted(api.label(), cause));
    }

    private void send(ExternalApi api, String reason, String message) {
        String key = api.name() + "|" + reason;
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        // 같은 날 같은 사유면 한 번만. putIfAbsent 가 아니라 merge 로 날짜까지 비교해, 자정을 넘기면 다시 보낸다.
        LocalDate previous = notifiedOn.put(key, today);
        if (today.equals(previous)) {
            return;
        }
        // 알림이 실패해도 호출 경로를 막지 않는다 — 관측이지 기능이 아니다(ExternalApiCallRecorder 와 같은 규칙).
        try {
            notifier.send(message);
        } catch (RuntimeException e) {
            log.warn("보조 키 알림 전송 실패 api={} reason={} cause={}", api, reason, e.getClass().getSimpleName());
        }
    }
}
