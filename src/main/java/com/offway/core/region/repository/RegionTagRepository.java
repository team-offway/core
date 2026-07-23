package com.offway.core.region.repository;

import com.offway.core.region.domain.RegionTagType;
import java.util.List;

/** 도메인이 의존하는 port. 구현은 {@link RegionTagRepositoryImpl}. */
public interface RegionTagRepository {

    /** 해당 태그가 붙은 지역 수. */
    long countByTag(RegionTagType tag);

    /** 해당 태그가 붙은 지역 ID 목록 (정책→지역 역방향 조회). */
    List<Long> findRegionIdsByTag(RegionTagType tag);
}
