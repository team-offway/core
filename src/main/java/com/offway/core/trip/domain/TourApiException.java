package com.offway.core.trip.domain;

import com.offway.core.common.exception.BaseException;
import com.offway.core.common.exception.ErrorCode;

/** 관광정보(TourAPI) 조회 관련 예외. */
public final class TourApiException extends BaseException {

    private TourApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    private TourApiException(ErrorCode errorCode, Throwable cause, boolean stackTraceUseful) {
        super(errorCode, cause, stackTraceUseful);
    }

    /** TourAPI(KorService2) 호출·파싱 실패 — 원인은 cause 체인·로그에만 남기고 응답엔 고정 문구가 나간다. */
    public static TourApiException lookupFailed(Throwable cause) {
        return new TourApiException(TourApiErrorCode.TOUR_LOOKUP_FAILED, cause);
    }

    /**
     * 캐시가 이미 잡아 둔 조회 실패를 재사용 — 원인은 최초 적재 시점에 loader 가 이미 로그로 남겼다(#362).
     *
     * <p>{@link #lookupFailed(Throwable)} 와 응답 계약(502/TOUR-001)은 같지만, 매번 새로 만드는 합성
     * 예외라 스택트레이스가 항상 같은 모양이고 새 진단 정보를 담지 않는다 — {@code stackTraceUseful=false}.
     */
    public static TourApiException cachedLookupFailure() {
        return new TourApiException(TourApiErrorCode.TOUR_LOOKUP_FAILED, null, false);
    }

    /** 관광빅데이터(방문자 통계) 호출·파싱 실패. */
    public static TourApiException dataLabLookupFailed(Throwable cause) {
        return new TourApiException(TourApiErrorCode.DATALAB_LOOKUP_FAILED, cause);
    }

    /** 전국문화축제표준데이터 호출·파싱 실패(#433). */
    public static TourApiException festivalStandardLookupFailed(Throwable cause) {
        return new TourApiException(TourApiErrorCode.FESTIVAL_STANDARD_LOOKUP_FAILED, cause);
    }

    /** 요청한 장소(POI)가 관광정보에 없음 — 404. */
    public static TourApiException poiNotFound() {
        return new TourApiException(TourApiErrorCode.POI_NOT_FOUND, null);
    }

    /** 관광정보 서비스를 쓸 수 없음(키 미설정 등) — 502. 상세 조회 시 "장소 없음(404)"과 구분한다. */
    public static TourApiException serviceUnavailable() {
        return new TourApiException(TourApiErrorCode.TOUR_SERVICE_UNAVAILABLE, null);
    }
}

