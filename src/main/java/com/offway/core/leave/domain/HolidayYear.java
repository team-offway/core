package com.offway.core.leave.domain;

import java.time.LocalDate;
import java.time.Year;

/**
 * 공휴일 목록으로 물을 수 있는 연도(#317).
 *
 * <p><b>왜 범위를 두는가.</b> 한 해를 물으면 그 해의 열두 달이 필요한데, {@link HolidayRefreshWindow} 가
 * 미리 채워두는 것은 <b>지난달부터 {@value HolidayRefreshWindow#MONTHS_AHEAD}개월</b>뿐이다. 그 밖의 달은
 * 조회 시점에 외부(특일정보)로 넘어간다 — 즉 <b>연도를 아무 값이나 받으면 요청 한 번이 외부 호출 열두 번</b>이
 * 되고, 그만큼 일일 한도를 태우며 호출당 timeout 이 곱해진다. 사용자가 아니라 우리 API 설계가 한도를 태우는
 * 전형적인 모양이다.
 *
 * <p><b>왜 하필 앞뒤 한 해인가.</b> 적재 창이 이번 달 기준 뒤로 13개월이라 <b>내년까지</b>는 대부분 이미
 * DB 에 있다. 지난해는 지난 여행의 연차를 되짚는 경우가 있어 연다. 그보다 먼 연도는 아직 공표조차 안 된
 * 경우가 많아(공휴일은 연 단위로 공표된다) 물어도 빈 답이 오기 쉽다.
 *
 * <p>거절은 조용히 하지 않는다 — 범위 밖이면 400 으로 답해, 앱이 "공휴일이 없는 해" 로 잘못 읽지 않게 한다.
 */
public record HolidayYear(int value) {

    /** 과거로 열어 두는 해 — 지난 여행의 연차를 되짚는 경우. */
    public static final int YEARS_BACK = 1;

    /** 미래로 열어 두는 해 — 적재 창(13개월)이 내년을 대부분 덮는다. */
    public static final int YEARS_AHEAD = 1;

    /**
     * 오늘을 기준으로 물을 수 있는 해인지 확인하고 만든다.
     *
     * @param today 기준일. 서버 시계가 아니라 호출자가 넘긴다 — 테스트가 연말 경계를 고정할 수 있어야 한다
     * @throws LeaveException 범위 밖이면 {@code LEAVE-015}
     */
    public static HolidayYear of(int value, LocalDate today) {
        int current = today.getYear();
        if (value < current - YEARS_BACK || value > current + YEARS_AHEAD) {
            throw LeaveException.holidayYearOutOfRange();
        }
        return new HolidayYear(value);
    }

    /** 그 해의 첫날. */
    public LocalDate start() {
        return LocalDate.of(value, 1, 1);
    }

    /** 그 해의 마지막 날 — 윤년을 직접 계산하지 않는다. */
    public LocalDate end() {
        return Year.of(value).atMonth(12).atEndOfMonth();
    }
}
