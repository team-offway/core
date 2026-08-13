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

    /** 샌드위치 조회 개월 수가 허용 범위(1~12)를 벗어남. */
    public static LeaveException invalidLookupMonths() {
        return new LeaveException(LeaveErrorCode.INVALID_LOOKUP_RANGE);
    }

    /** 날짜 구간과 기간스타일 중 하나만 골라야 하는데 둘 다거나 둘 다 아님. */
    public static LeaveException ambiguousPeriodInput() {
        return new LeaveException(LeaveErrorCode.AMBIGUOUS_PERIOD_INPUT);
    }

    /** 주말 포함 스타일인데 붙일 요일(브릿지)이 없음. */
    public static LeaveException weekendBridgeRequired() {
        return new LeaveException(LeaveErrorCode.WEEKEND_BRIDGE_REQUIRED);
    }

    /** 연차만 이어서 스타일인데 연차 일수가 없음. */
    public static LeaveException leaveDaysRequired() {
        return new LeaveException(LeaveErrorCode.LEAVE_DAYS_REQUIRED);
    }

    /** 이어서 쓸 연차 일수가 허용 범위(2~3)를 벗어남. */
    public static LeaveException invalidConnectedLeaveDays() {
        return new LeaveException(LeaveErrorCode.INVALID_CONNECTED_LEAVE_DAYS);
    }

    /** 기간스타일을 골랐는데 해석 기준일이 없음. */
    public static LeaveException baseDateRequired() {
        return new LeaveException(LeaveErrorCode.BASE_DATE_REQUIRED);
    }

    /** 총 연차가 음수·상한 초과·0.5 단위가 아님. */
    public static LeaveException invalidTotalLeaveDays() {
        return new LeaveException(LeaveErrorCode.INVALID_TOTAL_LEAVE_DAYS);
    }

    /** 사용 내역 증감이 0 이거나 0.5 단위가 아님. */
    public static LeaveException invalidLeaveUsageDays() {
        return new LeaveException(LeaveErrorCode.INVALID_LEAVE_USAGE_DAYS);
    }

    /** 소유 키 헤더가 비었거나 너무 김. */
    public static LeaveException invalidOwnerId() {
        return new LeaveException(LeaveErrorCode.INVALID_OWNER_ID);
    }

    /** 지우려는 사용 내역이 없거나 남의 것. */
    public static LeaveException leaveUsageNotFound() {
        return new LeaveException(LeaveErrorCode.LEAVE_USAGE_NOT_FOUND);
    }

    /** 사용 내역을 음수로 등록하려 함 — 취소는 삭제로 한다. */
    public static LeaveException leaveUsageReversalNotAllowed() {
        return new LeaveException(LeaveErrorCode.LEAVE_USAGE_REVERSAL_NOT_ALLOWED);
    }

    /** 코스 확정으로 기록된 내역을 연차 화면에서 지우려 함. */
    public static LeaveException courseLeaveUsageNotDeletable() {
        return new LeaveException(LeaveErrorCode.COURSE_LEAVE_USAGE_NOT_DELETABLE);
    }
}
