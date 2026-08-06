package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import java.util.List;

/** 방문자 집계 영속 port. 구현은 {@link RegionVisitorAggregateRepositoryImpl}. */
public interface RegionVisitorAggregateRepository {

    List<RegionVisitorAggregate> findAll();

    /**
     * 집계 전체를 새 것으로 <b>교체</b>한다.
     *
     * <p>부분 갱신하지 않는다 — 관광빅데이터는 달마다 통째로 다시 받는 값이라, 옛 달의 지역이 남아 있으면
     * 서로 다른 달이 섞인 랭킹이 된다.
     */
    void replaceAll(List<RegionVisitorAggregate> aggregates);
}
