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

    /**
     * 직전 조회 실패가 짧은 TTL 로 캐시된 상태. 원인은 그 조회 시점에 스택과 함께 로그로 남았으므로 여기서는 다시 들지 않는다
     * — 실패가 이어지는 동안 매 요청이 외부 read-timeout 을 다시 물지 않게 같은 실패로 즉시 응답한다.
     */
    public static HolidayException lookupFailedRecently() {
        return new HolidayException(HolidayErrorCode.HOLIDAY_LOOKUP_FAILED, null);
    }
}
