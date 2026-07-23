package com.offway.core.region.repository;

import com.offway.core.region.domain.Region;
import java.util.List;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data에 위임. */
@Repository
public class RegionRepositoryImpl implements RegionRepository {

    private final RegionJpaRepository regionJpaRepository;

    public RegionRepositoryImpl(RegionJpaRepository regionJpaRepository) {
        this.regionJpaRepository = regionJpaRepository;
    }

    @Override
    public long count() {
        return regionJpaRepository.count();
    }

    @Override
    public List<Region> findAll() {
        return regionJpaRepository.findAll();
    }

    @Override
    public List<Region> findByIds(List<Long> ids) {
        return regionJpaRepository.findAllById(ids);
    }
}
