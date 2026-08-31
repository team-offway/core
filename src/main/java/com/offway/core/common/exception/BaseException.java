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
    private final boolean stackTraceUseful;

    protected BaseException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).message());
        this.errorCode = errorCode;
        this.stackTraceUseful = true;
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).message(), cause);
        this.errorCode = errorCode;
        this.stackTraceUseful = true;
    }

    /**
     * @param stackTraceUseful 이 예외의 스택트레이스가 진단에 새 정보를 주는가. 캐시가 이미 로그로 남긴 실패를
     *     재사용해 새로 만드는 예외처럼 스택이 매번 같은 모양이라 정보가 없을 때만 {@code false} 를 준다 —
     *     {@link GlobalExceptionHandler} 가 5xx 를 로그할 때 이 값을 보고 스택트레이스를 붙일지 정한다.
     */
    protected BaseException(ErrorCode errorCode, Throwable cause, boolean stackTraceUseful) {
        super(requireErrorCode(errorCode).message(), cause);
        this.errorCode = errorCode;
        this.stackTraceUseful = stackTraceUseful;
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode 는 null 일 수 없습니다.");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public boolean stackTraceUseful() {
        return stackTraceUseful;
    }

    @Override
    public HttpStatus httpStatus() {
        return errorCode.category().httpStatus();
    }
}
