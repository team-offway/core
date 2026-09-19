package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.RegionFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** {@link RegionFeedbackRepository} 어댑터 — Spring Data 에 위임한다. */
@Repository
@RequiredArgsConstructor
public class RegionFeedbackRepositoryImpl implements RegionFeedbackRepository {

    private final RegionFeedbackJpaRepository jpaRepository;

    @Override
    public RegionFeedback save(RegionFeedback feedback) {
        return jpaRepository.save(feedback);
    }
}
