package com.offway.core.common.response;

import com.offway.core.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 *
 * <p>HTTP 204 는 사용하지 않는다 — 래퍼가 항상 body 를 만들므로 내릴 데이터가 없으면 200 + {@link #ok()}(data=null) 로 응답한다. 3xx
 * 리다이렉트는 body 가 아니라 {@code Location} 헤더를 소비하므로 이 래퍼를 쓰지 않는다.
 *
 * @param status body 의 status. HTTP status 와 항상 일치시킨다.
 * @param data 성공 페이로드. 실패 시 null.
 * @param detail 사용자 대면 문구.
 * @param code 성공은 {@code OK}, 실패는 도메인 에러코드.
 * @param pageResponse 페이지네이션 메타. 없으면 null.
 */
public record ApiResponseBody<T>(int status, T data, String detail, String code, PageResponse pageResponse) {

    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_DETAIL = "요청이 정상 처리되었습니다.";

    public static <T> ApiResponseBody<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponseBody<T> ok(T data, PageResponse pageResponse) {
        return success(HttpStatus.OK, data, pageResponse);
    }

    /** 내릴 데이터가 없는 성공 응답 (204 대신 200 + data=null). */
    public static <T> ApiResponseBody<T> ok() {
        return success(HttpStatus.OK, null, null);
    }

    public static <T> ApiResponseBody<T> created(T data) {
        return success(HttpStatus.CREATED, data, null);
    }

    public static <T> ApiResponseBody<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    /** detail 을 구체 사유로 덮어쓰는 실패 응답 (Bean Validation 필드 메시지 등). */
    public static <T> ApiResponseBody<T> fail(ErrorCode errorCode, String detail) {
        return new ApiResponseBody<>(errorCode.category().httpStatus().value(), null, detail, errorCode.code(), null);
    }

    private static <T> ApiResponseBody<T> success(HttpStatus status, T data, PageResponse pageResponse) {
        return new ApiResponseBody<>(status.value(), data, SUCCESS_DETAIL, SUCCESS_CODE, pageResponse);
    }
}
