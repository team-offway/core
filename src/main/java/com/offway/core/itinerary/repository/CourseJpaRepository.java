package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CourseJpaRepository extends JpaRepository<Course, Long> {

    List<Course> findByGuestIdOrderByIdDesc(String guestId);
}
