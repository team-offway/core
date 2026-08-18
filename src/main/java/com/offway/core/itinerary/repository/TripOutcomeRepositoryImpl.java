package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.TripOutcome;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class TripOutcomeRepositoryImpl implements TripOutcomeRepository {

    private final TripOutcomeJpaRepository jpaRepository;

    @Override
    public Set<Long> findAnsweredCourseIds(String guestId) {
        return jpaRepository.findAnsweredCourseIds(guestId);
    }

    @Override
    public Set<Long> findAnsweredCourseIdsIn(Collection<Long> courseIds) {
        return courseIds.isEmpty() ? Set.of() : jpaRepository.findAnsweredCourseIdsIn(courseIds);
    }

    @Override
    public TripOutcome save(TripOutcome outcome) {
        return jpaRepository.save(outcome);
    }

    @Override
    public int deleteByGuestId(String guestId) {
        return jpaRepository.deleteByGuestId(guestId);
    }
}
