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

    /** 저장 요청의 코스 구성이 불변식을 어김(순서·좌표 등). */
    public static ItineraryException invalidCourse() {
        return new ItineraryException(ItineraryErrorCode.INVALID_COURSE);
    }

    /** 요청한 코스가 없음. */
    public static ItineraryException courseNotFound() {
        return new ItineraryException(ItineraryErrorCode.COURSE_NOT_FOUND);
    }

    /** 여행 날짜 없이 저장된 코스로 연차 차감을 요청했다. */
    public static ItineraryException travelDateMissing() {
        return new ItineraryException(ItineraryErrorCode.TRAVEL_DATE_MISSING);
    }

    /** 홈 모달 "다녀오셨나요?" 에 이미 답한 여행이다. */
    public static ItineraryException tripAlreadyAnswered() {
        return new ItineraryException(ItineraryErrorCode.TRIP_ALREADY_ANSWERED);
    }

    /** 아직 끝나지 않은 여행에 다녀왔는지를 답하려 했다. */
    public static ItineraryException tripNotEnded() {
        return new ItineraryException(ItineraryErrorCode.TRIP_NOT_ENDED);
    }
}
