package com.offway.core.notification.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 알림 관련 예외(#263). */
public final class NotificationException extends BaseException {

    private NotificationException(ErrorCode errorCode) {
        super(errorCode);
    }

    // 소유 키 형식 예외(invalidOwnerId)는 소유자가 인증된 UUID 가 되면서 던질 일이 없어
    // 사라졌다(#280). 에러코드 상수는 append-only 라 그대로 둔다(NotificationErrorCode#INVALID_OWNER_ID).

    /** 요청한 알림이 없거나 소유자가 아니다 — 둘을 구분하지 않는다. */
    public static NotificationException notificationNotFound() {
        return new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
