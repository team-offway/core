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
    COURSE_NOT_FOUND("ITINERARY-003", ErrorCategory.NOT_FOUND, "요청한 코스를 찾을 수 없습니다."),

    /** 여행 날짜 없이 저장된 코스로 연차 차감을 요청했다 — 차감 일수를 계산할 근거가 없다. */
    TRAVEL_DATE_MISSING("ITINERARY-004", ErrorCategory.BAD_REQUEST, "여행 날짜가 없는 코스는 연차를 차감할 수 없습니다."),

    /** 홈 모달 "다녀오셨나요?" 에 이미 답한 여행이다(#116). */
    TRIP_ALREADY_ANSWERED("ITINERARY-005", ErrorCategory.CONFLICT, "이미 답한 여행입니다."),

    /** 아직 끝나지 않은 여행에 다녀왔는지를 답하려 했다(#116). */
    TRIP_NOT_ENDED("ITINERARY-006", ErrorCategory.CONFLICT, "아직 끝나지 않은 여행입니다."),

    /** 저장 코스의 여행 날짜를 지난 날짜로 고치려 했다(#170). 날짜 선택은 클라이언트 입력이라 400. */
    TRAVEL_DATE_IN_PAST("ITINERARY-007", ErrorCategory.BAD_REQUEST, "지난 날짜로는 여행 날짜를 바꿀 수 없습니다."),

    /** 공유 링크의 토큰이 없다 — 잘못된 링크이거나 애초에 발급된 적이 없다(#143). */
    SHARE_NOT_FOUND("ITINERARY-008", ErrorCategory.NOT_FOUND, "없는 공유 링크입니다."),

    /**
     * 공유 링크는 살아 있는데 코스가 지워졌다(#143).
     *
     * <p>{@link #SHARE_NOT_FOUND} 와 나누는 이유: 받은 사람이 "링크를 잘못 눌렀나" 와 "게시자가 지웠구나" 를
     * 구분할 수 있어야 한다. 앞의 것은 자기 탓을 하게 만들고, 뒤의 것은 사실을 알려준다.
     */
    SHARE_COURSE_DELETED("ITINERARY-009", ErrorCategory.GONE, "게시자가 삭제한 코스입니다.");

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
