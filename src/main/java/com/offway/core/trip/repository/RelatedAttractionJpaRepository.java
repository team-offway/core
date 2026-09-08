package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RelatedAttraction;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RelatedAttractionJpaRepository extends JpaRepository<RelatedAttraction, Long> {

    List<RelatedAttraction> findByRegionIdAndHubCodeAndCategoryLargeOrderByRelatedRankAsc(
            long regionId, String hubCode, String categoryLarge, Pageable pageable);

    List<RelatedAttraction> findByRegionId(long regionId);

    @Query("select max(related.baseYm) from RelatedAttraction related where related.regionId = :regionId")
    String findMaxBaseYm(@Param("regionId") long regionId);

    @Modifying
    @Query("delete from RelatedAttraction related where related.regionId = :regionId")
    int deleteByRegionId(@Param("regionId") long regionId);
}
