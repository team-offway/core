package com.offway.core.region.repository;

import com.offway.core.region.domain.RegionTagType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 도메인이 의존하는 port. 구현은 {@link RegionTagRepositoryImpl}. */
public interface RegionTagRepository {

    /** 해당 태그가 붙은 지역 수. */
    long countByTag(RegionTagType tag);

    /** 해당 태그가 붙은 지역 ID 목록 (정책→지역 역방향 조회). */
    List<Long> findRegionIdsByTag(RegionTagType tag);

    /** 한 지역에 붙은 태그 목록 (지역→정책 정방향 매칭). */
    List<RegionTagType> findTagsByRegionId(Long regionId);

    /**
     * 여러 지역의 태그를 한 번에 (지역ID → 태그 집합). 홈·추천처럼 후보가 여럿일 때 쓴다 — 지역마다
     * {@link #findTagsByRegionId} 를 부르면 후보 수만큼 쿼리가 늘어난다(N+1).
     *
     * <p>태그가 하나도 없는 지역은 <b>키가 아예 없다</b>. 호출자는 빈 집합으로 취급한다.
     */
    Map<Long, Set<RegionTagType>> findTagsByRegionIds(List<Long> regionIds);
}
