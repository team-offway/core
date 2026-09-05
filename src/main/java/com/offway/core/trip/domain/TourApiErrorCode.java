package com.offway.core.trip.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 관광정보(TourAPI) 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum TourApiErrorCode implements ErrorCode {

    /** TourAPI(KorService2) 호출·응답 파싱 실패. 외부 의존성이라 502. */
    TOUR_LOOKUP_FAILED("TOUR-001", ErrorCategory.EXTERNAL_API, "관광 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."),

    /** 관광빅데이터(방문자 통계) 호출·응답 파싱 실패. 외부 의존성이라 502. */
    DATALAB_LOOKUP_FAILED("TOUR-002", ErrorCategory.EXTERNAL_API, "관광 방문자 통계를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."),

    /** 요청한 장소(POI)가 관광정보에 없음. 정상 요청이 닿을 수 있는 계약이라 404. */
    POI_NOT_FOUND("TOUR-003", ErrorCategory.NOT_FOUND, "요청한 장소를 찾을 수 없습니다."),

    /**
     * 관광정보 서비스를 쓸 수 없음(키 미설정 등). 외부 의존성 불가라 502.
     *
     * <p>"조회 불가"를 "장소 없음(404)"과 분리하기 위한 별도 코드다. 키가 없으면 상세 조회를 빈 결과로 돌려주면 안 된다 —
     * 그러면 모든 장소가 존재하지 않는 것으로 둔갑한다.
     */
    TOUR_SERVICE_UNAVAILABLE("TOUR-004", ErrorCategory.EXTERNAL_API, "관광 정보 서비스를 사용할 수 없습니다."),

    /** 전국문화축제표준데이터 호출·파싱 실패(#433). 적재 배치만 타는 경로라 사용자가 직접 보는 일은 드물다. */
    FESTIVAL_STANDARD_LOOKUP_FAILED(
            "TOUR-005", ErrorCategory.EXTERNAL_API, "축제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    TourApiErrorCode(String code, ErrorCategory category, String message) {
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
