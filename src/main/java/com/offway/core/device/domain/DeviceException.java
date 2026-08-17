package com.offway.core.device.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 기기(푸시 토큰) 관련 예외(#264). */
public final class DeviceException extends BaseException {

    private DeviceException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 소유 키가 비었거나 너무 길다. */
    public static DeviceException invalidOwnerId() {
        return new DeviceException(DeviceErrorCode.INVALID_OWNER_ID);
    }

    /** 푸시 토큰이 비었거나 너무 길다. */
    public static DeviceException invalidPushToken() {
        return new DeviceException(DeviceErrorCode.INVALID_PUSH_TOKEN);
    }
}
