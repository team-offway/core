package com.offway.core.itinerary.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 코스(itinerary) 관련 예외. */
public final class ItineraryException extends BaseException {

    private ItineraryException(ErrorCode errorCode) {
        super(errorCode);
    }

    /** 지역에 배치할 볼거리가 없어 코스를 만들 수 없음. */
    public static ItineraryException courseNotBuildable() {
        return new ItineraryException(ItineraryErrorCode.COURSE_NOT_BUILDABLE);
    }
}
