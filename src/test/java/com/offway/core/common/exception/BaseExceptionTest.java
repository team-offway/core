package com.offway.core.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BaseExceptionTest {

    /** 테스트 전용 에러코드 — 실제 도메인 enum 과 독립적으로 계약만 검증한다. */
    private enum TestErrorCode implements ErrorCode {
        CONFLICTED("TEST-001", ErrorCategory.CONFLICT, "이미 처리된 요청입니다."),
        EXTERNAL_FAILED("TEST-002", ErrorCategory.EXTERNAL_API, "외부 서비스를 이용할 수 없습니다.");

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
        private TestException(ErrorCode errorCode) {
            super(errorCode);
        }

        private TestException(ErrorCode errorCode, Throwable cause) {
            super(errorCode, cause);
        }
    }

    @Test
    void status는_errorCode의_카테고리에서_파생된다() {
        BaseException exception = new TestException(TestErrorCode.CONFLICTED);

        assertEquals(HttpStatus.CONFLICT, exception.httpStatus());
    }

    @Test
    void 외부의존성_실패는_502로_파생된다() {
        BaseException exception = new TestException(TestErrorCode.EXTERNAL_FAILED);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.httpStatus());
    }

    @Test
    void errorCode를_그대로_보유한다() {
        BaseException exception = new TestException(TestErrorCode.CONFLICTED);

        assertSame(TestErrorCode.CONFLICTED, exception.errorCode());
        assertEquals("TEST-001", exception.errorCode().code());
    }

    @Test
    void 메시지는_errorCode의_문구를_따른다() {
        BaseException exception = new TestException(TestErrorCode.CONFLICTED);

        assertEquals("이미 처리된 요청입니다.", exception.getMessage());
    }

    @Test
    void cause를_보존한다() {
        RuntimeException cause = new RuntimeException("read timeout");

        BaseException exception = new TestException(TestErrorCode.EXTERNAL_FAILED, cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void errorCode가_null이면_생성에_실패한다() {
        assertThrows(NullPointerException.class, () -> new TestException(null));
    }
}
