package com.offway.core.leave.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 특일정보(공휴일) 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum HolidayErrorCode implements ErrorCode {

    /** 특일정보 외부 API 호출·응답 파싱 실패. 외부 의존성이라 502. */
    HOLIDAY_LOOKUP_FAILED("HOLIDAY-001", ErrorCategory.EXTERNAL_API, "공휴일 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    HolidayErrorCode(String code, ErrorCategory category, String message) {
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
