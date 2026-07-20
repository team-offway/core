package com.offway.core.common.exception;

import com.offway.core.common.response.ApiResponseBody;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 모든 예외를 {@link ApiResponseBody} 실패 응답으로 매핑한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String FIELD_ERROR_DELIMITER = ", ";
    private static final String FIELD_ERROR_FORMAT = "%s: %s";

    /**
     * 도메인 커스텀 예외 — status·code·detail 을 {@link ErrorCode} 에서 파생한다.
     *
     * <p>4xx 는 클라이언트 계약 위반이라 서버 입장에서 정상 흐름(info, 스택 없음). 5xx(외부 의존성 실패 등)는 조치 대상이라 스택과 함께 warn.
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponseBody<Void>> handleBaseException(BaseException exception) {
        ErrorCode errorCode = exception.errorCode();
        if (exception.httpStatus().is5xxServerError()) {
            log.warn("도메인 예외(5xx) code={} status={}", errorCode.code(), exception.httpStatus().value(), exception);
        } else {
            log.info("도메인 예외 code={} status={}", errorCode.code(), exception.httpStatus().value());
        }
        return ResponseEntity.status(exception.httpStatus()).body(ApiResponseBody.fail(errorCode));
    }

    /** Bean Validation 실패 — detail 에 "필드명: 메시지" 를 싣는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseBody<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining(FIELD_ERROR_DELIMITER));
        log.info("요청 검증 실패 detail={}", detail);
        return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.category().httpStatus())
                .body(ApiResponseBody.fail(CommonErrorCode.INVALID_REQUEST, detail));
    }

    /**
     * 처리하지 못한 예외 — 서버 버그·불변식 위반 신호.
     *
     * <p>원인은 로그에만 남기고 응답 detail 은 고정 문구로 내려 내부 정보가 새지 않게 한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseBody<Void>> handleUnexpectedException(Exception exception) {
        log.error("처리하지 못한 예외", exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.category().httpStatus())
                .body(ApiResponseBody.fail(CommonErrorCode.INTERNAL_ERROR));
    }

    private static String describe(FieldError fieldError) {
        return FIELD_ERROR_FORMAT.formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
