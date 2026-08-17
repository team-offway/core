package com.offway.core.device.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 기기(푸시 토큰) 관련 에러 사유(#264).
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum DeviceErrorCode implements ErrorCode {

    /** 소유 키(게스트 ID) 가 비었거나 너무 길다. 빈 헤더는 {@code @RequestHeader} 를 통과하므로 정상 요청이 닿는다. */
    INVALID_OWNER_ID("DEVICE-001", ErrorCategory.BAD_REQUEST, "게스트 식별자가 올바르지 않습니다."),

    /**
     * 푸시 토큰이 비었거나 너무 길다.
     *
     * <p><b>메시지에 토큰을 담지 않는다.</b> detail 은 그대로 응답에 나가고 로그에도 남는데, 토큰은
     * 비밀값에 준한다(로깅 규약).
     */
    INVALID_PUSH_TOKEN("DEVICE-002", ErrorCategory.BAD_REQUEST, "푸시 토큰이 올바르지 않습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    DeviceErrorCode(String code, ErrorCategory category, String message) {
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
