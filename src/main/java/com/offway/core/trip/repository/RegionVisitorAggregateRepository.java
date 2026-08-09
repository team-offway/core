package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** 방문자 집계 영속 port. 구현은 {@link RegionVisitorAggregateRepositoryImpl}. */
public interface RegionVisitorAggregateRepository {

    List<RegionVisitorAggregate> findAll();

    /**
     * 저장된 집계 중 <b>가장 최근</b> 기준 연월. 갱신이 필요한지 판단하는 데만 쓴다.
     *
     * <p>전량 교체({@link #replaceAll})라 평상시엔 모든 행의 연월이 같지만, 그 전제에 기대지 않고
     * 최댓값을 본다 — 전제가 깨진 상태(부분 적재·수동 개입)에서 옛 달을 집으면 "이미 최신" 으로 잘못
     * 판단해 갱신이 영영 멈춘다.
     */
    Optional<YearMonth> latestBaseMonth();

    /** 저장된 집계 행 수 — 로그용. 건수만 필요한 자리에서 전체 행을 적재하지 않으려고 둔다. */
    long count();

    /**
     * 집계 전체를 새 것으로 <b>교체</b>한다.
     *
     * <p>부분 갱신하지 않는다 — 관광빅데이터는 달마다 통째로 다시 받는 값이라, 옛 달의 지역이 남아 있으면
     * 서로 다른 달이 섞인 랭킹이 된다.
     */
    void replaceAll(List<RegionVisitorAggregate> aggregates);
}
