package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.CourseShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface CourseShareJpaRepository extends JpaRepository<CourseShare, Long> {

    Optional<CourseShare> findByShareToken(String shareToken);

    Optional<CourseShare> findByCourseId(Long courseId);

    List<CourseShare> findByCourseIdIn(Collection<Long> courseIds);
}
