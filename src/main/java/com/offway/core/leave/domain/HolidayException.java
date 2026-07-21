package com.offway.core.leave.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 특일정보 조회 관련 예외. */
public final class HolidayException extends BaseException {

    private HolidayException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /** 외부 API 호출·파싱 실패 — 원인은 cause 체인·로그에만 남기고 응답엔 고정 문구가 나간다. */
    public static HolidayException lookupFailed(Throwable cause) {
        return new HolidayException(HolidayErrorCode.HOLIDAY_LOOKUP_FAILED, cause);
    }
}
