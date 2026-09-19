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
    SHARE_COURSE_DELETED("ITINERARY-009", ErrorCategory.GONE, "게시자가 삭제한 코스입니다."),

    /** 자차 코스에 대중교통 수단을 고정하려 한 경우(#456) — 역·터미널을 해석할 것이 없어 아무것도 바뀌지 않는다. */
    TRANSIT_MODE_ON_NON_TRANSIT("ITINERARY-010", ErrorCategory.BAD_REQUEST, "대중교통 코스에서만 이동수단을 바꿀 수 있습니다."),

    /**
     * 여행지 평가가 범위를 벗어났다 — 별점이 1~5 밖이거나 한 줄이 상한을 넘었다(#592).
     *
     * <p>메시지에 실제 값을 담지 않는다 — detail 은 그대로 사용자에게 나가는 자리이고, 한 줄은
     * 사용자가 친 원본이다.
     */
    INVALID_TRIP_FEEDBACK("ITINERARY-011", ErrorCategory.BAD_REQUEST, "여행지 평가 값이 올바르지 않습니다."),

    /**
     * 안 갔다고 답하면서 여행지 평가를 함께 보냈다(#592).
     *
     * <p><b>조용히 버리지 않는다.</b> 클라이언트는 평가를 남겼다고 여기는데 데이터가 없고, 그 어긋남이
     * 아무 흔적도 남기지 않는다. 모달은 안 갔다고 누르면 평가 화면을 띄우지 않으므로 정상 흐름으로는
     * 닿지 않는다 — 닿았다면 클라이언트 쪽이 어긋난 것이라 알려 주는 편이 낫다.
     */
    FEEDBACK_ON_UNVISITED_TRIP(
            "ITINERARY-012", ErrorCategory.BAD_REQUEST, "다녀오지 않은 여행에는 평가를 남길 수 없습니다.");

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
