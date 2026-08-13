package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.CourseShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class CourseShareRepositoryImpl implements CourseShareRepository {

    private final CourseShareJpaRepository courseShareJpaRepository;

    @Override
    public CourseShare save(CourseShare courseShare) {
        return courseShareJpaRepository.save(courseShare);
    }

    @Override
    public Optional<CourseShare> findByShareToken(String shareToken) {
        return courseShareJpaRepository.findByShareToken(shareToken);
    }

    @Override
    public Optional<CourseShare> findByCourseId(Long courseId) {
        return courseShareJpaRepository.findByCourseId(courseId);
    }

    @Override
    public List<CourseShare> findByCourseIdIn(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            // 빈 IN 절은 방언마다 다르게 취급된다. 부를 이유도 없으므로 여기서 끊는다.
            return List.of();
        }
        return courseShareJpaRepository.findByCourseIdIn(courseIds);
    }
}
