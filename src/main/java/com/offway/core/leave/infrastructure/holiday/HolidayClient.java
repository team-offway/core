package com.offway.core.leave.infrastructure.holiday;

import java.time.LocalDate;
import java.util.Set;

/**
 * 특일정보(공휴일·대체공휴일) 조회 port.
 *
 * <p>도메인·서비스는 이 인터페이스에만 의존한다. 실제 외부 API(한국천문연구원 특일정보) 호출은 adapter 가 맡는다(DIP).
 */
public interface HolidayClient {

    /**
     * 해당 연·월의 공휴일 날짜를 조회한다.
     *
     * @param solYear 양력 연도 (예: 2026)
     * @param solMonth 양력 월 (1~12)
     * @return 공휴일·대체공휴일 날짜 집합. 키가 없으면 빈 집합(로컬 실행성)
     */
    Set<LocalDate> getHolidays(int solYear, int solMonth);
}
