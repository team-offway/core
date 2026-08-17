package com.offway.core.notification.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 알림 관련 에러 사유(#263).
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum NotificationErrorCode implements ErrorCode {

    /** 소유 키(게스트 ID) 가 비었거나 너무 길다. 빈 헤더는 {@code @RequestHeader} 를 통과하므로 정상 요청이 닿는다. */
    INVALID_OWNER_ID("NOTIFICATION-001", ErrorCategory.BAD_REQUEST, "게스트 식별자가 올바르지 않습니다."),

    /**
     * 요청한 알림이 없거나 소유자가 아니다.
     *
     * <p><b>둘을 나누지 않는다.</b> 남의 알림에 403 을 주면 "그 id 는 존재하는데 네 것이 아니다" 를 알려주는
     * 셈이라, id 를 훑어 남의 알림 존재를 확인할 수 있다.
     */
    NOTIFICATION_NOT_FOUND("NOTIFICATION-002", ErrorCategory.NOT_FOUND, "요청한 알림을 찾을 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    NotificationErrorCode(String code, ErrorCategory category, String message) {
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
