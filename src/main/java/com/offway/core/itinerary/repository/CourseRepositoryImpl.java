package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    public List<Course> findByUserId(UUID userId) {
        return courseJpaRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Override
    public Page<Course> findByUserId(UUID userId, Pageable pageable) {
        return courseJpaRepository.findByUserIdOrderByIdDesc(userId, pageable);
    }

    @Override
    public List<Course> findByTravelDate(LocalDate travelDate) {
        return courseJpaRepository.findByTravelDateAndUserIdIsNotNull(travelDate);
    }

    @Override
    public List<Course> findUpcoming(UUID userId, LocalDate today) {
        return courseJpaRepository.findByUserIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(userId, today);
    }

    @Override
    public Page<Course> findUpcoming(UUID userId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findByUserIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
                userId, today, pageable);
    }

    @Override
    public List<Course> findPast(UUID userId, LocalDate today) {
        return courseJpaRepository.findByUserIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(userId, today);
    }

    @Override
    public Page<Course> findPast(UUID userId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findByUserIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(
                userId, today, pageable);
    }

    @Override
    public Optional<Course> findByIdAndUserId(Long id, UUID userId) {
        return courseJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public void delete(Course course) {
        courseJpaRepository.delete(course);
    }

    /** 파생 delete 라 엔티티를 로드해 지운다 — cascade·orphanRemoval 이 그대로 적용돼 하위가 고아로 남지 않는다. */
    @Override
    public int deleteByUserId(UUID userId) {
        return courseJpaRepository.deleteByUserId(userId);
    }
}
