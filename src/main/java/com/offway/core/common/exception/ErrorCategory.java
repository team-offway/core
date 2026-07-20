package com.offway.core.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 범주 → HTTP 상태 1:1 매핑.
 *
 * <p>예외는 status 를 직접 들지 않고 이 카테고리에서 파생한다.
 */
public enum ErrorCategory {

    /** 클라이언트 요청이 계약을 위반함 (형식·값 범위 등). */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    /** 인증되지 않음. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** 인증됐으나 권한이 없음. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** 대상 리소스가 없음. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 지원하지 않는 HTTP 메서드. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** 현재 상태와 요청이 충돌함 (이미 확정된 일정 재확정 등). */
    CONFLICT(HttpStatus.CONFLICT),

    /** 지원하지 않는 Content-Type. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /** 외부 의존성(TourAPI·TAGO·TMAP·특일정보 등) 호출·파싱 실패. */
    EXTERNAL_API(HttpStatus.BAD_GATEWAY),

    /** 서버 내부 오류. */
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCategory(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
