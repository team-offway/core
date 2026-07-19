package com.offway.core.region.repository;

import com.offway.core.region.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionJpaRepository extends JpaRepository<Region, Long> {
}
