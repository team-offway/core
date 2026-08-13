package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class LeaveUsageRepositoryImpl implements LeaveUsageRepository {

    private final LeaveUsageJpaRepository jpaRepository;

    @Override
    public List<LeaveUsage> findByGuestId(String guestId) {
        return jpaRepository.findByGuestIdOrderByUsedOnDescIdDesc(guestId);
    }

    @Override
    public double sumDaysByGuestId(String guestId) {
        return jpaRepository.sumDaysByGuestId(guestId);
    }

    @Override
    public boolean existsByGuestIdAndCourseId(String guestId, Long courseId) {
        return jpaRepository.existsByGuestIdAndCourseId(guestId, courseId);
    }

    @Override
    public Optional<LeaveUsage> findByGuestIdAndCourseId(String guestId, Long courseId) {
        return jpaRepository.findByGuestIdAndCourseId(guestId, courseId);
    }

    @Override
    public Set<Long> findDeductedCourseIds(String guestId) {
        return jpaRepository.findDeductedCourseIds(guestId);
    }

    @Override
    public int deleteByGuestIdAndCourseId(String guestId, Long courseId) {
        return jpaRepository.deleteByGuestIdAndCourseId(guestId, courseId);
    }

    @Override
    public LeaveUsage save(LeaveUsage usage) {
        return jpaRepository.save(usage);
    }
}
