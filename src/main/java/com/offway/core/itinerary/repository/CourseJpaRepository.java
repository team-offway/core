package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CourseJpaRepository extends JpaRepository<Course, Long> {

    List<Course> findByGuestIdOrderByIdDesc(String guestId);

    // travelDate 가 null 인 코스는 두 조건 모두에 걸리지 않아 자연히 빠진다 — DB 마다 다른 NULL 정렬에 기대지 않는다.
    List<Course> findByGuestIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
            String guestId, LocalDate today);

    List<Course> findByGuestIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(String guestId, LocalDate today);

    Optional<Course> findByIdAndGuestId(Long id, String guestId);
}
