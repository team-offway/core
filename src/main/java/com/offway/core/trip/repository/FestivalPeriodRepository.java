package com.offway.core.trip.repository;

import com.offway.core.trip.domain.FestivalPeriod;
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
}
