package com.offway.core.leave.infrastructure.holiday;

import java.time.LocalDate;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * {@link HolidayClient} 외부 경계 stub — 통합 테스트에서 특일정보 외부 호출을 격리한다.
 *
 * <p>응답을 람다로 받아 각 테스트가 본문에서 교체한다. default 람다는 throw 라, 명시 세팅을 빠뜨리면 즉시 깨져 "이전 테스트 상태가
 * 살아남는" 함정을 막는다.
 */
public class StubHolidayClient implements HolidayClient {

    private BiFunction<Integer, Integer, Set<LocalDate>> behavior = (year, month) -> {
        throw new IllegalStateException("StubHolidayClient 미설정 — 테스트가 respond(...) 로 동작을 지정해야 합니다.");
    };

    /** 각 테스트가 (연, 월) → 공휴일 집합 동작을 지정한다. */
    public void respond(BiFunction<Integer, Integer, Set<LocalDate>> behavior) {
        this.behavior = behavior;
    }

    @Override
    public Set<LocalDate> getHolidays(int solYear, int solMonth) {
        return behavior.apply(solYear, solMonth);
    }
}
