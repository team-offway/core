package com.offway.core.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 5xx 도메인 예외의 로그 — 스택트레이스를 붙이는지 여부(#360 계열).
 *
 * <p>Spring 컨텍스트 없이 핸들러 메서드를 직접 호출한다. 로깅은 {@code exception} 을 인자로 넘기느냐
 * 마느냐로 갈리는 순수한 분기라, MockMvc 왕복 없이도 전부 검증된다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private enum TestErrorCode implements ErrorCode {
        EXTERNAL_FAILED("TEST-EXT", ErrorCategory.EXTERNAL_API, "외부 서비스를 이용할 수 없습니다.");

        private final String code;
        private final ErrorCategory category;
        private final String message;

        TestErrorCode(String code, ErrorCategory category, String message) {
            this.code = code;
            this.category = category;
            this.message = message;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public ErrorCategory category() {
            return category;
        }

        @Override
        public String message() {
            return message;
        }
    }

    private static final class TestException extends BaseException {
        private TestException(Throwable cause, boolean stackTraceUseful) {
            super(TestErrorCode.EXTERNAL_FAILED, cause, stackTraceUseful);
        }
    }

    @Test
    void 새_진단_정보가_있는_5xx는_스택트레이스와_함께_찍는다() {
        ILoggingEvent event = captureWarnLog(new TestException(new RuntimeException("read timeout"), true));

        assertNotNull(event.getThrowableProxy(), "스택트레이스가 없음 — 원인을 진단할 수 없다");
    }

    @Test
    void 캐시가_이미_로그로_남긴_실패는_스택트레이스_없이_찍는다() {
        // 실제 원인은 캐시 loader 가 최초 적재 시점에 이미 로그로 남겼다. 여기서 또 찍으면 매 요청마다
        // 같은 모양의 스택이 중복돼, 정보 없이 로그만 쌓인다(PoiDetailService.CachedDetail.orThrow()).
        ILoggingEvent event = captureWarnLog(new TestException(null, false));

        assertNull(event.getThrowableProxy(), "정보 없는 스택트레이스가 여전히 찍힘");
    }

    /** 스택 유무와 무관하게 code·status 는 한 줄에 남아야 한다 — 몇 건이 발생했는지는 여전히 세야 한다. */
    @Test
    void 스택을_생략해도_code와_status는_로그_한_줄에_남는다() {
        ILoggingEvent event = captureWarnLog(new TestException(null, false));

        String message = event.getFormattedMessage();
        assertEquals(true, message.contains(TestErrorCode.EXTERNAL_FAILED.code()));
        assertEquals(true, message.contains("502"));
    }

    private ILoggingEvent captureWarnLog(BaseException exception) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handleBaseException(exception);
        } finally {
            logger.detachAppender(appender);
        }
        List<ILoggingEvent> warnEvents = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        assertEquals(1, warnEvents.size(), "WARN 로그가 정확히 한 줄이어야 한다: " + appender.list);
        return warnEvents.get(0);
    }
}
