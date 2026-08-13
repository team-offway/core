package com.offway.core.leave.domain;

import java.time.DayOfWeek;

/**
 * 주말에 붙일 평일 하루 — 주말 앞(금)이냐 뒤(월)냐. 어느 쪽이든 연차는 1일, 구간은 2박 3일이다(결정 #38).
 *
 * <p>와이어프레임(7/20)의 "언제 하루 더 쉴래요? 금요일 / 토요일" 이 이 선택이다. 라벨은 <b>여행 시작 요일</b>을 묻고,
 * 이 enum 은 그 결과로 <b>연차를 쓰는 요일</b>까지 함께 안다 — 금요일 출발이면 금요일 연차(금·토·일), 토요일 출발이면
 * 월요일 연차(토·일·월)다. 클라이언트는 라벨만 고르고, 어느 날 연차가 빠지는지는 서버가 계산한다.
 */
public enum WeekendBridge {

    /** 금요일 연차 — 금·토·일. 와이어프레임 라벨 "금요일". */
    FRIDAY(DayOfWeek.FRIDAY),

    /** 월요일 연차 — 토·일·월. 와이어프레임 라벨 "토요일"(토요일 출발). */
    MONDAY(DayOfWeek.SATURDAY);

    /** 이 선택에서 여행이 시작되는 요일. 종료일은 여기서 2일 뒤다(2박 3일). */
    private final DayOfWeek startDayOfWeek;

    WeekendBridge(DayOfWeek startDayOfWeek) {
        this.startDayOfWeek = startDayOfWeek;
    }

    DayOfWeek startDayOfWeek() {
        return startDayOfWeek;
    }
}
