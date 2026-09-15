package com.offway.core.liveactivity.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** Live Activity 토큰 관련 예외(#575). */
public final class LiveActivityException extends BaseException {

    private LiveActivityException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 푸시 토큰이 비었거나 너무 길다. */
    public static LiveActivityException invalidPushToken() {
        return new LiveActivityException(LiveActivityErrorCode.INVALID_PUSH_TOKEN);
    }

    /** 코스 식별자가 없거나 0 이하다. */
    public static LiveActivityException invalidCourseId() {
        return new LiveActivityException(LiveActivityErrorCode.INVALID_COURSE_ID);
    }

    /** 그 코스가 없거나 내 것이 아니다 — 둘을 가르지 않는다. */
    public static LiveActivityException courseNotFound() {
        return new LiveActivityException(LiveActivityErrorCode.COURSE_NOT_FOUND);
    }
}
