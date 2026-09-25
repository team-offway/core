package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.RegionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RegionFeedbackJpaRepository extends JpaRepository<RegionFeedback, Long> {
}
