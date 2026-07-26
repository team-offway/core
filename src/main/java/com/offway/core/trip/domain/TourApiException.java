package com.offway.core.trip.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 관광정보(TourAPI) 조회 관련 예외. */
public final class TourApiException extends BaseException {

    private TourApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /** TourAPI(KorService2) 호출·파싱 실패 — 원인은 cause 체인·로그에만 남기고 응답엔 고정 문구가 나간다. */
    public static TourApiException lookupFailed(Throwable cause) {
        return new TourApiException(TourApiErrorCode.TOUR_LOOKUP_FAILED, cause);
    }

    /** 관광빅데이터(방문자 통계) 호출·파싱 실패. */
    public static TourApiException dataLabLookupFailed(Throwable cause) {
        return new TourApiException(TourApiErrorCode.DATALAB_LOOKUP_FAILED, cause);
    }

    /** 요청한 장소(POI)가 관광정보에 없음 — 404. */
    public static TourApiException poiNotFound() {
        return new TourApiException(TourApiErrorCode.POI_NOT_FOUND, null);
    }
}

