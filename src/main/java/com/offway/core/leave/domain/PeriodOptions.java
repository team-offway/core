package com.offway.core.leave.domain;

/**
 * 기간스타일의 보조 파라미터. 스타일만으로는 구간이 정해지지 않아 와이어프레임(7/20)이 서브질문을 하나 더 던진다 —
 * 주말 포함이면 "언제 하루 더 쉴래요?"(브릿지), 연차만이면 "얼마나 쓸래요?"(연차 일수).
 *
 * <p>스타일마다 <b>필요한 값이 다르므로 둘 다 선택 필드</b>다. 대신 "이 스타일에 이 값이 있어야 한다"는 계약을 이
 * 객체가 소유해, 각 스타일은 {@code requiredXxx()} 를 부르기만 한다({@link PeriodStyle} 에 null 검사가 흩어지지 않게).
 *
 * <p>여기서 던지는 예외는 <b>계약</b>이다(400) — 클라이언트가 스타일에 맞는 보조 파라미터를 빠뜨리면 정상 요청으로 닿는다.
 *
 * @param weekendBridge 주말에 붙일 평일 하루 ({@link PeriodStyle#WEEKEND} 에 필요)
 * @param leaveDays 이어서 쓸 연차 일수 ({@link PeriodStyle#CONNECTED} 에 필요)
 */
public record PeriodOptions(WeekendBridge weekendBridge, Integer leaveDays) {

    /** 연차만 이어서 쓸 때의 최소 일수 — 1일이면 당일치기와 같아지므로 그쪽 스타일을 쓴다(결정 #38 "평일 2~3일"). */
    public static final int MIN_CONNECTED_LEAVE_DAYS = 2;

    /** 최대 일수 — 여행 상한과 같다. 늘리려면 코스 생성(Day1~3)을 함께 열어야 한다. */
    public static final int MAX_CONNECTED_LEAVE_DAYS = AvailableTime.MAX_TRIP_DAYS;

    /** 보조 파라미터가 없는 스타일(당일치기)용. */
    public static PeriodOptions none() {
        return new PeriodOptions(null, null);
    }

    WeekendBridge requiredWeekendBridge() {
        if (weekendBridge == null) {
            throw LeaveException.weekendBridgeRequired();
        }
        return weekendBridge;
    }

    int requiredLeaveDays() {
        if (leaveDays == null) {
            throw LeaveException.leaveDaysRequired();
        }
        if (leaveDays < MIN_CONNECTED_LEAVE_DAYS || leaveDays > MAX_CONNECTED_LEAVE_DAYS) {
            throw LeaveException.invalidConnectedLeaveDays();
        }
        return leaveDays;
    }
}
