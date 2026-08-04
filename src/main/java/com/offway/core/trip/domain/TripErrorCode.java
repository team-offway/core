package com.offway.core.trip.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 여행지·장소 조회 관련 에러 사유(#144). 외부 관광 API 와 무관한, 우리 데이터에 대한 계약이다.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum TripErrorCode implements ErrorCode {

    /** 분류가 종류에 속하지 않음(예: 숙소 탭에 카페 분류). 클라이언트가 조합을 잘못 보낸 것이라 400. */
    CATEGORY_KIND_MISMATCH("TRIP-001", ErrorCategory.BAD_REQUEST, "장소 분류가 올바르지 않습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    TripErrorCode(String code, ErrorCategory category, String message) {
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
