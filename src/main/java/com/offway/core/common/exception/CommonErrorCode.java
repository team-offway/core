package com.offway.core.common.exception;

/**
 * 특정 도메인에 속하지 않는 공통 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum CommonErrorCode implements ErrorCode {

    /** Bean Validation 등 요청 값 계약 위반. detail 은 호출부가 구체 사유로 덮어쓴다. */
    INVALID_REQUEST("COMMON-400", ErrorCategory.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    /** 매핑되는 엔드포인트·리소스가 없음. */
    NOT_FOUND("COMMON-404", ErrorCategory.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    /** 해당 경로가 지원하지 않는 HTTP 메서드. */
    METHOD_NOT_ALLOWED("COMMON-405", ErrorCategory.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    /** Accept 헤더가 요구하는 형식으로 응답할 수 없음. */
    NOT_ACCEPTABLE("COMMON-406", ErrorCategory.NOT_ACCEPTABLE, "요청하신 형식으로 응답할 수 없습니다."),

    /** 지원하지 않는 Content-Type. */
    UNSUPPORTED_MEDIA_TYPE("COMMON-415", ErrorCategory.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),

    /** 처리하지 못한 서버 오류. 원인은 응답이 아니라 로그·cause 체인에 남긴다. */
    INTERNAL_ERROR("COMMON-500", ErrorCategory.INTERNAL, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    CommonErrorCode(String code, ErrorCategory category, String message) {
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
