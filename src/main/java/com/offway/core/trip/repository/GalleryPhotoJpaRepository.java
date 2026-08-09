package com.offway.core.trip.repository;

import com.offway.core.trip.domain.GalleryPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface GalleryPhotoJpaRepository extends JpaRepository<GalleryPhoto, Long> {

    List<GalleryPhoto> findByRegionId(Long regionId);

    List<GalleryPhoto> findByRegionIdIn(List<Long> regionIds);

    @Query("select count(p) from GalleryPhoto p where p.regionId is not null")
    long countWithRegion();
}
