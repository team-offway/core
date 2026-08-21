package com.offway.core.trip.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 여행지·장소 조회 도메인 예외(#144). */
public final class TripException extends BaseException {

    private TripException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 분류가 종류에 속하지 않는다 — 숙소 탭에 카페 분류를 물은 경우 등. */
    public static TripException categoryKindMismatch() {
        return new TripException(TripErrorCode.CATEGORY_KIND_MISMATCH);
    }

    public static TripException regionNotFound() {
        return new TripException(TripErrorCode.REGION_NOT_FOUND);
    }
}
