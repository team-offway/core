package com.offway.core.notification.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 알림 관련 예외(#263). */
public final class NotificationException extends BaseException {

    private NotificationException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 소유 키가 비었거나 너무 길다. */
    public static NotificationException invalidOwnerId() {
        return new NotificationException(NotificationErrorCode.INVALID_OWNER_ID);
    }

    /** 요청한 알림이 없거나 소유자가 아니다 — 둘을 구분하지 않는다. */
    public static NotificationException notificationNotFound() {
        return new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
