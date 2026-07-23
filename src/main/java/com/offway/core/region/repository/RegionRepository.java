package com.offway.core.region.repository;

import com.offway.core.region.domain.Region;
import java.util.List;

/** 도메인이 의존하는 port. 구현은 {@link RegionRepositoryImpl}. */
public interface RegionRepository {

    long count();

    List<Region> findAll();

    /** ID 다건 조회 (정책→되는 지역 목록 등). */
    List<Region> findByIds(List<Long> ids);
}
