package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CourseJpaRepository extends JpaRepository<Course, Long> {

    List<Course> findByGuestIdOrderByIdDesc(String guestId);

    // 페이지 변형. 애그리거트(days·slots)는 fetch join 하지 않는다 — 컬렉션을 조인하면 페이징이
    // 메모리에서 일어난다(HHH000104). 지연 로딩은 default_batch_fetch_size 가 묶어 준다.
    Page<Course> findByGuestIdOrderByIdDesc(String guestId, Pageable pageable);

    // travelDate 가 null 인 코스는 두 조건 모두에 걸리지 않아 자연히 빠진다 — DB 마다 다른 NULL 정렬에 기대지 않는다.
    List<Course> findByGuestIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
            String guestId, LocalDate today);

    Page<Course> findByGuestIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
            String guestId, LocalDate today, Pageable pageable);

    List<Course> findByGuestIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(String guestId, LocalDate today);

    Page<Course> findByGuestIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(
            String guestId, LocalDate today, Pageable pageable);

    Optional<Course> findByIdAndGuestId(Long id, String guestId);

    int deleteByGuestId(String guestId);
}
