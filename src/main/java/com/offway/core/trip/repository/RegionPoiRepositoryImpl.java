package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionPoi;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class RegionPoiRepositoryImpl implements RegionPoiRepository {

    private final RegionPoiJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RegionPoi> findShowable(long regionId, int limit) {
        return jpaRepository.findShowable(regionId, Limit.of(limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionPoi> findForCards(List<Long> regionIds, int perCategory) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findForCards(regionIds, perCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFresh(long regionId, YearMonth baseYm) {
        return jpaRepository.existsFresh(regionId, RegionPoi.format(baseYm));
    }

    /**
     * <b>지역 하나가 한 트랜잭션이다.</b> 지우고 넣는 사이가 갈라지면 그 순간 조회한 사용자에게 빈 지역이
     * 보인다. 반대로 89곳을 통째로 묶으면 한 지역의 실패가 그날 전체를 되돌린다 — 배치가 지역마다
     * 이 메서드를 부르므로 실패도 지역 단위로 갇힌다.
     */
    @Override
    @Transactional
    public void replaceRegion(long regionId, List<RegionPoi> pois) {
        jpaRepository.deleteByRegionId(regionId);
        jpaRepository.saveAll(pois);
    }
}
