package com.offway.core.leave.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 연차·가용시간 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum LeaveErrorCode implements ErrorCode {

    /** 여행 종료일이 시작일보다 앞섬 — 요청 DTO 계약 위반(멀쩡한 클라이언트가 정상 요청으로는 닿지 않음). */
    INVALID_DATE_RANGE("LEAVE-001", ErrorCategory.BAD_REQUEST, "여행 종료일은 시작일과 같거나 이후여야 합니다."),

    /** 여행 구간이 상한(2박 3일)을 넘음 — 코스 생성이 Day1~3 만 지원한다(결정 #38). */
    TRIP_TOO_LONG("LEAVE-002", ErrorCategory.BAD_REQUEST, "여행 구간은 최대 2박 3일까지 가능합니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    LeaveErrorCode(String code, ErrorCategory category, String message) {
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
