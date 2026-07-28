package com.offway.core.itinerary.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 코스(itinerary) 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum ItineraryErrorCode implements ErrorCode {

    /** 지역에 배치할 볼거리가 없어 코스를 만들 수 없음. 정상 요청이 닿을 수 있는 계약이라 404. */
    COURSE_NOT_BUILDABLE("ITINERARY-001", ErrorCategory.NOT_FOUND, "이 지역으로는 만들 수 있는 코스가 없습니다. 다른 지역을 골라 주세요."),

    /** 저장 요청의 코스 구성이 유효하지 않음(슬롯 순서·좌표 등 불변식 위반). 클라이언트 입력이라 400. */
    INVALID_COURSE("ITINERARY-002", ErrorCategory.BAD_REQUEST, "코스 구성이 올바르지 않습니다."),

    /** 요청한 코스가 없음. 404. */
    COURSE_NOT_FOUND("ITINERARY-003", ErrorCategory.NOT_FOUND, "요청한 코스를 찾을 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    ItineraryErrorCode(String code, ErrorCategory category, String message) {
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
