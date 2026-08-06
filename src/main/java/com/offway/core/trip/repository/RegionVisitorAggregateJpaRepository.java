package com.offway.core.trip.repository;

import com.offway.core.trip.domain.RegionVisitorAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionVisitorAggregateJpaRepository extends JpaRepository<RegionVisitorAggregate, Long> {
}
