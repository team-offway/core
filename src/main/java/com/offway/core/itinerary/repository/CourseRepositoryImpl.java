package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class CourseRepositoryImpl implements CourseRepository {

    private final CourseJpaRepository courseJpaRepository;

    @Override
    public Course save(Course course) {
        return courseJpaRepository.save(course);
    }

    @Override
    public Optional<Course> findById(Long id) {
        return courseJpaRepository.findById(id);
    }

    @Override
    public List<Course> findByGuestId(String guestId) {
        return courseJpaRepository.findByGuestIdOrderByIdDesc(guestId);
    }

    @Override
    public Page<Course> findByGuestId(String guestId, Pageable pageable) {
        return courseJpaRepository.findByGuestIdOrderByIdDesc(guestId, pageable);
    }

    @Override
    public List<Course> findByTravelDate(LocalDate travelDate) {
        return courseJpaRepository.findByTravelDateAndGuestIdIsNotNull(travelDate);
    }

    @Override
    public List<Course> findUpcoming(String guestId, LocalDate today) {
        return courseJpaRepository.findByGuestIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(guestId, today);
    }

    @Override
    public Page<Course> findUpcoming(String guestId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findByGuestIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
                guestId, today, pageable);
    }

    @Override
    public List<Course> findPast(String guestId, LocalDate today) {
        return courseJpaRepository.findByGuestIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(guestId, today);
    }

    @Override
    public Page<Course> findPast(String guestId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findByGuestIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(
                guestId, today, pageable);
    }

    @Override
    public Optional<Course> findByIdAndGuestId(Long id, String guestId) {
        return courseJpaRepository.findByIdAndGuestId(id, guestId);
    }

    @Override
    public void delete(Course course) {
        courseJpaRepository.delete(course);
    }

    /** 파생 delete 라 엔티티를 로드해 지운다 — cascade·orphanRemoval 이 그대로 적용돼 하위가 고아로 남지 않는다. */
    @Override
    public int deleteByGuestId(String guestId) {
        return courseJpaRepository.deleteByGuestId(guestId);
    }
}
