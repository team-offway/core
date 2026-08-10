package com.offway.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    @Test
    void HTTP_실패는_응답_본문을_사유로_남긴다() {
        // data.go.kr 은 일일 한도 초과를 응답 본문의 returnReasonCode 로 알려준다. 예외 메시지에는 URL 만
        // 있고 그 코드가 없어, 본문을 봐야 일일 한도인지 순간 속도 제한인지 갈린다(#224).
        Throwable error = tooManyRequests(
                "<returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>"
                        + "<returnReasonCode>22</returnReasonCode>");

        String rendered = RootCause.of(error);

        assertTrue(rendered.contains("429"), "상태코드가 있어야 한다. 실제=" + rendered);
        assertTrue(rendered.contains("returnReasonCode"), "본문이 있어야 한다. 실제=" + rendered);
    }

    @Test
    void 재시도로_감싸여도_응답_본문에_닿는다() {
        // 429 는 재시도를 거치므로 RetryExhaustedException 으로 감싸여 온다. 껍데기만 보면 사유를 놓친다.
        Throwable wrapped = new IllegalStateException(
                "Retries exhausted: 3/3", tooManyRequests("<returnReasonCode>22</returnReasonCode>"));

        String rendered = RootCause.of(wrapped);

        assertTrue(rendered.contains("returnReasonCode"), "실제=" + rendered);
    }

    @Test
    void 응답_본문의_비밀값도_가린다() {
        // 본문에도 키가 실려 올 수 있다. 예외 메시지와 같은 마스킹 경로를 통과해야 규칙이 두 벌이 되지 않는다.
        Throwable error = tooManyRequests("rejected serviceKey=abc123 for today");

        String rendered = RootCause.of(error);

        assertFalse(rendered.contains("abc123"), "키가 로그로 새면 안 된다. 실제=" + rendered);
        assertTrue(rendered.contains("serviceKey=***"), "실제=" + rendered);
    }

    @Test
    void 응답_본문이_비면_예외_메시지로_돌아간다() {
        // 본문 없이 상태만 오는 경우도 있다. 그때 빈 사유를 남기면 아무것도 안 남긴 것과 같다.
        Throwable error = tooManyRequests("");

        String rendered = RootCause.of(error);

        assertTrue(rendered.contains("429"), "실제=" + rendered);
    }

    @Test
    void 라벨은_HTTP_상태코드로_모은다() {
        // 집계용 키다. of() 는 본문까지 담아 건마다 달라지므로 집계 키로 쓰면 39건이 39종류가 된다.
        assertEquals("429", RootCause.label(tooManyRequests("<returnReasonCode>22</returnReasonCode>")));
    }

    @Test
    void 라벨은_HTTP_가_아니면_클래스명() {
        assertEquals("TimeoutException", RootCause.label(new TimeoutException("Did not observe any item")));
    }

    @Test
    void 라벨도_체인을_타고_내려간다() {
        Throwable wrapped = new IllegalStateException("Retries exhausted: 3/3", tooManyRequests("body"));

        assertEquals("429", RootCause.label(wrapped));
    }

    @Test
    void 라벨은_null_이면_unknown() {
        assertEquals("unknown", RootCause.label(null));
    }

    /** 본문을 가진 429 응답 예외 — 실제 외부가 주는 모양 그대로. */
    private static WebClientResponseException tooManyRequests(String body) {
        return WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
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
