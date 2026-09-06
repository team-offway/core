package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.notification.Notifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ExternalApiHealth} 단위 테스트.
 *
 * <p>핵심은 <b>언제 알리지 않는가</b>다. 실패마다 보내면 장애 한 번에 채널이 덮여 아무도 안 보므로,
 * 여기서 지켜야 할 것은 "울리는가" 보다 "안 울려야 할 때 조용한가" 다.
 */
class ExternalApiHealthTest {

    private static final String SYSTEM = "train";

    /** 보낸 문구를 모으는 stub — 내부 컴포넌트가 아니라 외부 경계(디스코드)라 stub 이 맞다. */
    private static final class RecordingNotifier implements Notifier {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(String message) {
            sent.add(message);
        }
    }

    @Test
    void 한두_번_실패로는_알리지_않는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");

        assertEquals(List.of(), notifier.sent, "외부는 원래 가끔 실패한다 — 이걸로 울리면 신호가 죽는다");
        assertTrue(health.isHealthy(SYSTEM));
    }

    @Test
    void 연속_세_번_실패하면_장애로_보고_한_번만_알린다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");

        assertEquals(1, notifier.sent.size(), "상태가 바뀔 때만 알린다 — 실패마다 보내면 채널이 덮인다");
        assertTrue(notifier.sent.getFirst().contains("장애"));
        assertTrue(notifier.sent.getFirst().contains(SYSTEM));
        assertFalse(health.isHealthy(SYSTEM));
    }

    @Test
    void 중간에_성공하면_연속_실패가_끊겨_알리지_않는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.succeeded(SYSTEM);
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");

        assertEquals(List.of(), notifier.sent, "간헐 실패는 장애가 아니다");
    }

    @Test
    void 장애_뒤_연속_성공하면_회복을_알린다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.succeeded(SYSTEM);
        health.succeeded(SYSTEM);

        assertEquals(2, notifier.sent.size());
        assertTrue(notifier.sent.getLast().contains("회복"));
        assertTrue(health.isHealthy(SYSTEM));
    }

    /**
     * 배포한 날 실제로 겪은 모양이다(#482). 게이트웨이가 깜빡이는 중이었고, 운 좋게 붙은 한 번을
     * "회복" 이라고 선언했다. 장애는 세 번을 요구하면서 회복은 한 번이면 뒤집히던 비대칭이 원인이다.
     */
    @Test
    void 한_번_성공했다_다시_죽으면_회복이_아니다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        assertEquals(1, notifier.sent.size(), "여기까지는 장애 알림 하나");

        health.succeeded(SYSTEM);
        health.failed(SYSTEM, "HTTP 500");

        assertEquals(1, notifier.sent.size(), "깜빡임에 회복을 선언하면 채널이 장애·회복으로 덮인다");
        assertFalse(health.isHealthy(SYSTEM), "성공 한 번으로는 장애에서 못 벗어난다");
    }

    /** 깜빡임이 반복돼도 알림은 늘지 않는다 — 잦아진 알림은 결국 안 읽힌다. */
    @Test
    void 깜빡임이_반복돼도_알림은_한_번뿐이다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        for (int i = 0; i < 5; i++) {
            health.succeeded(SYSTEM);
            health.failed(SYSTEM, "HTTP 500");
        }

        assertEquals(1, notifier.sent.size());
        assertFalse(health.isHealthy(SYSTEM));
    }

    /** 회복 뒤에는 다시 연속 세 번을 실패해야 장애다 — 회복이 실패 수를 지운다. */
    @Test
    void 회복하면_실패_수가_지워진다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.succeeded(SYSTEM);
        health.succeeded(SYSTEM);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");

        assertEquals(2, notifier.sent.size(), "장애 하나 · 회복 하나 그대로");
        assertTrue(health.isHealthy(SYSTEM));
    }

    @Test
    void 장애가_아니었으면_성공해도_회복을_알리지_않는다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.succeeded(SYSTEM);
        health.succeeded(SYSTEM);

        assertEquals(List.of(), notifier.sent, "평상시 성공은 알릴 것이 없다");
    }

    @Test
    void 시스템별로_따로_센다() {
        RecordingNotifier notifier = new RecordingNotifier();
        ExternalApiHealth health = new ExternalApiHealth(notifier);

        health.failed("train", "HTTP 500");
        health.failed("tour", "HTTP 500");
        health.failed("train", "HTTP 500");
        health.failed("tour", "HTTP 500");

        assertEquals(List.of(), notifier.sent, "둘을 합쳐 세면 멀쩡한 시스템 때문에 장애가 조기 선언된다");
        assertTrue(health.isHealthy("train"));
        assertTrue(health.isHealthy("tour"));
    }

    @Test
    void 알림이_터져도_판정은_그대로_남는다() {
        Notifier broken = message -> {
            throw new IllegalStateException("웹훅 죽음");
        };
        ExternalApiHealth health = new ExternalApiHealth(broken);

        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");
        health.failed(SYSTEM, "HTTP 500");

        assertFalse(health.isHealthy(SYSTEM), "관측이 기능을 막지 않는다 — 알림 실패가 상태를 되돌리면 안 된다");
    }
}
