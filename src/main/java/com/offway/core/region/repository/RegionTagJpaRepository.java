package com.offway.core.region.repository;

import com.offway.core.region.domain.RegionTag;
import com.offway.core.region.domain.RegionTagType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionTagJpaRepository extends JpaRepository<RegionTag, Long> {

    long countByTag(RegionTagType tag);

    @Query("SELECT rt.regionId FROM RegionTag rt WHERE rt.tag = :tag")
    List<Long> findRegionIdsByTag(@Param("tag") RegionTagType tag);

    @Query("SELECT rt.tag FROM RegionTag rt WHERE rt.regionId = :regionId")
    List<RegionTagType> findTagsByRegionId(@Param("regionId") Long regionId);

    /** (지역ID, 태그) 쌍을 여러 지역에 대해 한 번에. 지역마다 따로 부르면 후보 수만큼 쿼리가 늘어난다(N+1). */
    @Query("SELECT rt.regionId, rt.tag FROM RegionTag rt WHERE rt.regionId IN :regionIds")
    List<Object[]> findRegionIdAndTagByRegionIds(@Param("regionIds") List<Long> regionIds);
}
