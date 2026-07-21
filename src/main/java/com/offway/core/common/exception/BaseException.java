package com.offway.core.common.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * 도메인 커스텀 예외의 공통 부모.
 *
 * <p>status·code·message 를 직접 들지 않고 {@link ErrorCode} 에서 파생한다. 하위 클래스는 private 생성자 + static 팩토리
 * 메서드로 사유를 노출한다.
 */
public abstract class BaseException extends RuntimeException implements HttpMappable {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).message());
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).message(), cause);
        this.errorCode = errorCode;
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode 는 null 일 수 없습니다.");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus httpStatus() {
        return errorCode.category().httpStatus();
    }
}
