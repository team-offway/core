package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPeriod;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/** 도메인이 의존하는 port(#388). 구현은 {@link FestivalPeriodRepositoryImpl}. */
public interface FestivalPeriodRepository {

    /**
     * contentId → 기간. <b>기간을 모르는 축제는 키가 없다.</b>
     *
     * <p>빈 값을 넣어 돌려주지 않는 이유는 호출자가 "모른다" 와 "안 열린다" 를 구분해야 하기 때문이다 —
     * 모르는 것을 끝났다고 단정하면 있는 축제를 우리가 지운다.
     */
    Map<String, FestivalPeriod> findByContentIds(Collection<String> contentIds);

    /** 받아 온 기간을 저장한다 — 이미 있으면 갱신. 축제는 날짜가 바뀌고 취소된다. */
    int upsertAll(Collection<FestivalPeriod> periods);

    /**
     * 이번 수집에 없던 행을 걷어낸다(#388) — <b>온전히 훑은 회차에서만</b> 부른다.
     *
     * <p>페이지 상한에 걸렸거나 한 페이지라도 실패했으면 "없다" 를 단정할 수 없다. 그때 부르면 멀쩡한
     * 축제를 우리가 지운다.
     *
     * @param keptContentIds 이번에 받은 축제들
     * @param minEventEnd 이번 조회가 본 범위의 시작 — 그보다 앞서 끝난 행은 건드리지 않는다
     * @return 지운 행 수
     */
    int deleteMissingFrom(Collection<String> keptContentIds, LocalDate minEventEnd);
}
