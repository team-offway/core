package com.offway.core.region.repository;

import com.offway.core.region.domain.RegionTagType;
import java.util.List;
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
}
