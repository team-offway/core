package com.offway.core.leave.domain;

import com.offway.core.common.exception.ErrorCategory;
import com.offway.core.common.exception.ErrorCode;

/**
 * 연차·가용시간 관련 에러 사유.
 *
 * <p>번호는 append-only — 재사용·재배치하지 않고 결번을 유지한다.
 */
public enum LeaveErrorCode implements ErrorCode {

    /** 여행 종료일이 시작일보다 앞섬 — 요청 DTO 계약 위반(멀쩡한 클라이언트가 정상 요청으로는 닿지 않음). */
    INVALID_DATE_RANGE("LEAVE-001", ErrorCategory.BAD_REQUEST, "여행 종료일은 시작일과 같거나 이후여야 합니다."),

    /** 여행 구간이 상한(2박 3일)을 넘음 — 코스 생성이 Day1~3 만 지원한다(결정 #38). */
    TRIP_TOO_LONG("LEAVE-002", ErrorCategory.BAD_REQUEST, "여행 구간은 최대 2박 3일까지 가능합니다."),

    /** 샌드위치 조회 개월 수가 허용 범위(1~12)를 벗어남 — 요청 쿼리 계약 위반. */
    INVALID_LOOKUP_RANGE("LEAVE-003", ErrorCategory.BAD_REQUEST, "조회 기간은 1~12개월 사이여야 합니다."),

    /**
     * 날짜 구간(시작·종료 모두)과 기간스타일 중 정확히 하나를 골라야 하는데 둘 다거나 둘 다 아님 — 요청 모드 계약 위반.
     * 날짜를 한쪽만 보낸 경우도 여기 해당한다(그 모드를 고른 것으로 인정하지 않는다).
     */
    AMBIGUOUS_PERIOD_INPUT(
            "LEAVE-004", ErrorCategory.BAD_REQUEST, "여행 날짜를 직접 고르거나 기간 스타일을 고르거나, 한 가지만 선택해 주세요."),

    /** 주말 포함 스타일인데 붙일 요일(브릿지)이 없음. */
    WEEKEND_BRIDGE_REQUIRED(
            "LEAVE-005", ErrorCategory.BAD_REQUEST, "주말 포함 여행은 하루 더 쉴 요일을 함께 골라주세요."),

    /** 연차만 이어서 스타일인데 연차 일수가 없음. */
    LEAVE_DAYS_REQUIRED("LEAVE-006", ErrorCategory.BAD_REQUEST, "연차를 며칠 쓸지 함께 골라주세요."),

    /** 이어서 쓸 연차 일수가 허용 범위(2~3)를 벗어남. */
    INVALID_CONNECTED_LEAVE_DAYS(
            "LEAVE-007", ErrorCategory.BAD_REQUEST, "이어서 쓰는 연차는 2~3일까지 가능합니다."),

    /** 기간스타일을 골랐는데 해석 기준일이 없음 — 서버 시계로 대체하지 않는다(클라이언트 로컬 날짜가 정본). */
    BASE_DATE_REQUIRED("LEAVE-008", ErrorCategory.BAD_REQUEST, "기준 날짜를 함께 보내주세요."),

    /** 총 연차가 음수·상한 초과·0.5 단위가 아님. */
    INVALID_TOTAL_LEAVE_DAYS(
            "LEAVE-009", ErrorCategory.BAD_REQUEST, "연차 일수는 0.5일 단위로, 0일 이상 365일 이하여야 합니다."),

    /** 사용 내역 증감이 0 이거나 0.5 단위가 아님. 0 은 아무것도 바꾸지 않아 기록할 이유가 없다. */
    INVALID_LEAVE_USAGE_DAYS(
            "LEAVE-010", ErrorCategory.BAD_REQUEST, "연차 증감은 0.5일 단위여야 하고 0일은 기록할 수 없습니다.");

    private final String code;
    private final ErrorCategory category;
    private final String message;

    LeaveErrorCode(String code, ErrorCategory category, String message) {
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
