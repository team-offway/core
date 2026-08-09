package com.offway.core.trip.repository;

import com.offway.core.trip.domain.HubAttraction;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class HubAttractionRepositoryImpl implements HubAttractionRepository {

    private final HubAttractionJpaRepository jpaRepository;

    @Override
    public List<HubAttraction> findByRegionId(Long regionId) {
        return jpaRepository.findByRegionIdOrderByHubRankAsc(regionId);
    }

    @Override
    public List<HubAttraction> findByRegionIds(List<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRegionIdInOrderByRegionIdAscHubRankAsc(regionIds);
    }

    /**
     * 한 트랜잭션 안에서 비우고 새로 넣는다 — 중간 상태(순위가 빈 지역)가 조회에 보이지 않게.
     *
     * <p>외부 호출은 이 밖에서 이미 끝난 뒤다(영속성 규약).
     */
    @Override
    @Transactional
    public void replaceRegion(Long regionId, List<HubAttraction> attractions) {
        jpaRepository.deleteByRegionId(regionId);
        jpaRepository.saveAll(attractions);
    }

    @Override
    public long countRegionsWithMonthAtLeast(List<Long> regionIds, YearMonth month) {
        if (regionIds.isEmpty()) {
            return 0;
        }
        return jpaRepository.countRegionsWithBaseYmAtLeast(regionIds, HubAttraction.toBaseYm(month));
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
