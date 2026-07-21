package com.offway.core.leave.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 연차·가용시간 요청 계약 위반 예외. */
public final class LeaveException extends BaseException {

    private LeaveException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 종료일이 시작일보다 앞섬. */
    public static LeaveException invalidDateRange() {
        return new LeaveException(LeaveErrorCode.INVALID_DATE_RANGE);
    }

    /** 여행 구간이 상한(2박 3일)을 넘음. */
    public static LeaveException tripTooLong() {
        return new LeaveException(LeaveErrorCode.TRIP_TOO_LONG);
    }
}
