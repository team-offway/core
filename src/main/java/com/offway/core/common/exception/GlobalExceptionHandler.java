package com.offway.core.common.exception;

import com.offway.core.common.response.ApiResponseBody;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 {@link ApiResponseBody} 실패 응답으로 매핑한다.
 *
 * <p>{@link ResponseEntityExceptionHandler} 를 상속하는 이유: 깨진 JSON·잘못된 메서드·미지원 미디어타입·없는 경로 같은 프레임워크
 * 예외는 Spring 이 이미 올바른 4xx 를 판정해 들고 있다. 이를 상속하지 않고 {@code @ExceptionHandler(Exception.class)} 하나로
 * 받으면 그 status 를 500 으로 덮어써, 클라이언트 실수가 서버 오류로 둔갑하고 error 로그·스택까지 남는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    /** Bean Validation 실패 — detail 에 "필드명: 메시지" 를 싣는다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining(FIELD_ERROR_DELIMITER));
        log.info("요청 검증 실패 detail={}", detail);
        return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.category().httpStatus())
                .body(ApiResponseBody.fail(CommonErrorCode.INVALID_REQUEST, detail));
    }

    /**
     * 프레임워크가 처리하는 나머지 예외의 공통 출구 — 부모가 판정한 status 를 유지한 채 우리 래퍼로 감싼다.
     *
     * <p>부모의 기본 body 는 RFC 7807 ProblemDetail 이라 그대로 두면 응답 포맷이 엔드포인트마다 갈린다. detail 도 우리 고정 문구로
     * 덮어써 파서 예외 원문 같은 내부 정보가 새지 않게 한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.warn("프레임워크 예외(5xx) status={}", status.value(), exception);
        } else {
            log.info("프레임워크 예외 status={} type={}", status.value(), exception.getClass().getSimpleName());
        }
        return ResponseEntity.status(status).body(ApiResponseBody.fail(resolve(status)));
    }

    /** 프레임워크가 정한 status 를 우리 에러코드로 옮긴다. 매핑이 없는 4xx 는 계약 위반으로 뭉뚱그린다. */
    private static ErrorCode resolve(HttpStatusCode status) {
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return CommonErrorCode.NOT_FOUND;
        }
        if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return CommonErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.equals(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        if (status.is4xxClientError()) {
            return CommonErrorCode.INVALID_REQUEST;
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }

    private static String describe(FieldError fieldError) {
        return FIELD_ERROR_FORMAT.formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
