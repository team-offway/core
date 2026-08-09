package com.offway.core.leave.repository;

import com.offway.core.leave.domain.StoredHolidayMonth;
import java.time.YearMonth;
import java.util.List;

/** 공휴일 월 적재 영속 port(#193 3단계). 구현은 {@link HolidayMonthRepositoryImpl}. */
public interface HolidayMonthRepository {

    /**
     * 여러 달을 한 번에 — 기간 조회가 달마다 묻지 않게(N+1 방지).
     *
     * <p>없는 달은 결과에 <b>안 들어온다</b>. 호출자가 "받아온 적 없는 달" 로 구분해야 하기 때문에 빈 값으로
     * 채워 주지 않는다.
     */
    List<StoredHolidayMonth> findByMonths(List<YearMonth> months);

    /**
     * 주어진 달을 <b>교체</b>한다 — 전량 교체가 아니다.
     *
     * <p>공휴일은 달마다 독립이고 과거 달은 확정되면 안 바뀐다. 전량을 갈아끼우면 이번 갱신에서 다루지 않은
     * 과거 달까지 사라져, 그 달을 쓰는 계산이 다시 외부를 물게 된다.
     */
    void replaceMonths(List<StoredHolidayMonth> months);

    /** 저장된 달 수 — 적재가 돌았는지 판단하는 데 쓴다. */
    long count();
}
