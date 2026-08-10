package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * 로그에 남길 실패 사유 한 줄. 이 값이 곧 운영에서 원인을 찾는 유일한 실마리다.
 */
class RootCauseTest {

    @Test
    void 체인의_맨_끝_사유를_남긴다() {
        // 껍데기(ReactiveException·TourApiException)만 찍히던 것이 이 유틸이 생긴 이유다.
        Throwable root = new TimeoutException("Did not observe any item within 6000ms");
        Throwable wrapped = new IllegalStateException("outer", new RuntimeException("middle", root));

        assertEquals("TimeoutException: Did not observe any item within 6000ms", RootCause.of(wrapped));
    }

    @Test
    void 메시지가_없으면_클래스명만() {
        assertEquals("IllegalStateException", RootCause.of(new IllegalStateException()));
    }

    @Test
    void null_이면_unknown() {
        assertEquals("unknown", RootCause.of(null));
    }

    @Test
    void 자기_자신을_cause_로_가져도_멈춘다() {
        SelfReferencing error = new SelfReferencing();

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> RootCause.of(error));
    }

    @Test
    void 두_예외가_서로를_cause_로_가져도_멈춘다() {
        // 자기 참조만 막으면 A → B → A 는 그대로 통과해 무한 루프가 된다. 실패를 기록하려던 코드가
        // 프로세스를 잡아먹는다.
        Mutual first = new Mutual("first");
        Mutual second = new Mutual("second");
        first.link(second);
        second.link(first);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String rendered = RootCause.of(first);
            assertTrue(rendered.startsWith("Mutual"), "실제=" + rendered);
        });
    }

    @Test
    void 메시지의_비밀값을_가린다() {
        // WebClient 예외 메시지에는 요청 URL 이 통째로 들어올 수 있고, 우리는 serviceKey 를 쿼리에 싣는다.
        Throwable error = new RuntimeException("429 from GET https://x/y?serviceKey=abc123&areaCode=34");

        String rendered = RootCause.of(error);

        assertFalse(rendered.contains("abc123"), "키가 로그로 새면 안 된다. 실제=" + rendered);
        assertTrue(rendered.contains("serviceKey=***"), "실제=" + rendered);
    }

    @Test
    void 메시지의_개행으로_가짜_로그_줄을_만들_수_없다() {
        Throwable error = new RuntimeException("boom\n2026-01-01 INFO  fake line");

        String rendered = RootCause.of(error);

        assertFalse(rendered.contains("\n"), "실제=" + rendered);
        assertEquals("RuntimeException: boom2026-01-01 INFO  fake line", rendered);
    }

    @Test
    void 아주_긴_메시지는_잘라낸다() {
        // 외부 예외는 응답 본문 일부를 통째로 담기도 한다. 한 줄이 화면을 넘기면 앞뒤 줄까지 훑기 어려워진다.
        String rendered = RootCause.of(new RuntimeException("x".repeat(1000)));

        assertTrue(rendered.endsWith("…"), "잘렸다는 표식이 있어야 한다");
        assertTrue(rendered.length() < 260, "실제 길이=" + rendered.length());
    }

    /** cause 가 자기 자신인 예외 — 방어가 없으면 while 이 안 끝난다. */
    private static final class SelfReferencing extends RuntimeException {

        private SelfReferencing() {
            super("self");
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    /** 서로를 cause 로 가리키는 한 쌍 — 자기 참조 검사만으로는 못 막는다. */
    private static final class Mutual extends RuntimeException {

        private Throwable linked;

        private Mutual(String message) {
            super(message);
        }

        private void link(Throwable other) {
            this.linked = other;
        }

        @Override
        public synchronized Throwable getCause() {
            return linked;
        }
    }
}
