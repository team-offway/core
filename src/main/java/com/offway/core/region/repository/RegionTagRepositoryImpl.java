package com.offway.core.region.repository;

import com.offway.core.region.domain.RegionTagType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
public class RegionTagRepositoryImpl implements RegionTagRepository {

    private final RegionTagJpaRepository regionTagJpaRepository;

    public RegionTagRepositoryImpl(RegionTagJpaRepository regionTagJpaRepository) {
        this.regionTagJpaRepository = regionTagJpaRepository;
    }

    @Override
    public long countByTag(RegionTagType tag) {
        return regionTagJpaRepository.countByTag(tag);
    }

    @Override
    public List<Long> findRegionIdsByTag(RegionTagType tag) {
        return regionTagJpaRepository.findRegionIdsByTag(tag);
    }

    @Override
    public List<RegionTagType> findTagsByRegionId(Long regionId) {
        return regionTagJpaRepository.findTagsByRegionId(regionId);
    }

    @Override
    public Map<Long, Set<RegionTagType>> findTagsByRegionIds(List<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return Map.of(); // 빈 IN 절은 DB 마다 문법이 갈린다 — 쿼리를 아예 보내지 않는다.
        }
        Map<Long, Set<RegionTagType>> tagsByRegion = new HashMap<>();
        for (Object[] row : regionTagJpaRepository.findRegionIdAndTagByRegionIds(regionIds)) {
            Long regionId = (Long) row[0];
            RegionTagType tag = (RegionTagType) row[1];
            tagsByRegion.computeIfAbsent(regionId, id -> EnumSet.noneOf(RegionTagType.class)).add(tag);
        }
        return tagsByRegion;
    }
}
