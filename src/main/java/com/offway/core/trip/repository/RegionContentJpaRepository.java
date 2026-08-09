package com.offway.core.trip.repository;

import com.offway.core.trip.domain.StoredRegionContent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionContentJpaRepository extends JpaRepository<StoredRegionContent, Long> {

    List<StoredRegionContent> findByRegionIdIn(List<Long> regionIds);
}
