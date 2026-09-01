package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class LeaveUsageRepositoryImpl implements LeaveUsageRepository {

    private final LeaveUsageJpaRepository jpaRepository;

    @Override
    public List<LeaveUsage> findByUserId(UUID userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Override
    public double sumDaysByUserId(UUID userId) {
        return jpaRepository.sumDaysByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndCourseId(UUID userId, Long courseId) {
        return jpaRepository.existsByUserIdAndCourseId(userId, courseId);
    }

    @Override
    public Optional<LeaveUsage> findByUserIdAndCourseId(UUID userId, Long courseId) {
        return jpaRepository.findByUserIdAndCourseId(userId, courseId);
    }

    @Override
    public Set<Long> findDeductedCourseIds(UUID userId) {
        return jpaRepository.findDeductedCourseIds(userId);
    }

    @Override
    public Set<Long> findDeductedCourseIdsIn(Collection<Long> courseIds) {
        return courseIds.isEmpty() ? Set.of() : jpaRepository.findDeductedCourseIdsIn(courseIds);
    }

    @Override
    public int deleteByUserIdAndCourseId(UUID userId, Long courseId) {
        return jpaRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    @Override
    public Optional<LeaveUsage> findByIdAndUserId(Long id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public void delete(LeaveUsage usage) {
        jpaRepository.delete(usage);
    }

    @Override
    public LeaveUsage save(LeaveUsage usage) {
        return jpaRepository.save(usage);
    }

    @Override
    public int deleteByUserId(UUID userId) {
        return jpaRepository.deleteByUserId(userId);
    }
}
