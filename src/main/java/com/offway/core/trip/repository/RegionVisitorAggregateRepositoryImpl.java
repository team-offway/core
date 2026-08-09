package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class RegionVisitorAggregateRepositoryImpl implements RegionVisitorAggregateRepository {

    private final RegionVisitorAggregateJpaRepository jpaRepository;

    @Override
    public List<RegionVisitorAggregate> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Optional<YearMonth> latestBaseMonth() {
        return jpaRepository.findTopByOrderByBaseYmDesc().map(RegionVisitorAggregate::baseMonth);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    /**
     * 한 트랜잭션 안에서 비우고 새로 넣는다 — 중간 상태(빈 집계)가 조회에 보이지 않게.
     *
     * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약).
     */
    @Override
    @Transactional
    public void replaceAll(List<RegionVisitorAggregate> aggregates) {
        jpaRepository.deleteAllInBatch();
        jpaRepository.saveAll(aggregates);
    }
}
