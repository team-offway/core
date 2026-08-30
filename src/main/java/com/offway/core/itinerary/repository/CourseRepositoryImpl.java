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
    public List<Course> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : courseJpaRepository.findAllById(ids);
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

    /**
     * <b>후보를 시작일 범위로 좁혀 오고, 종료일 판정은 도메인에 맡긴다.</b>
     *
     * <p>종료일은 컬럼이 아니라 {@code travel_date + travel_days - 1} 로 계산된다. 그 계산을 질의에 옮겨 적으면
     * 규칙이 도메인과 SQL 두 곳에 살게 되는데, 이 값은 실제로 한 번 어긋난 적이 있다 — 예전에 일정이 있는 날의
     * 수를 기간으로 쓰다가 종료일이 하루 이르게 나왔고 <b>연차가 하루 덜 차감됐다</b>(#159·#164).
     * 규칙을 {@link Course#travelEndDate()} 한 곳에만 두면 그 종류의 어긋남이 생길 자리가 없다.
     *
     * <p>범위 폭은 코스 최대 기간이라 후보가 며칠치를 넘지 않는다 — 전부 훑는 것과 다르다.
     */
    @Override
    public List<Course> findEndedOn(LocalDate endedOn) {
        LocalDate earliestStart = endedOn.minusDays(Course.MAX_TRAVEL_DAYS - 1L);
        return courseJpaRepository.findByTravelDateBetweenAndUserIdIsNotNull(earliestStart, endedOn).stream()
                .filter(course -> endedOn.equals(course.travelEndDate()))
                .toList();
    }

    @Override
    public List<Course> findUpcoming(UUID userId, LocalDate today) {
        return courseJpaRepository.findUpcomingByEndDate(userId, today);
    }

    @Override
    public Page<Course> findUpcoming(UUID userId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findUpcomingByEndDate(userId, today, pageable);
    }

    @Override
    public List<Course> findPast(UUID userId, LocalDate today) {
        return courseJpaRepository.findPastByEndDate(userId, today);
    }

    @Override
    public Page<Course> findPast(UUID userId, LocalDate today, Pageable pageable) {
        return courseJpaRepository.findPastByEndDate(userId, today, pageable);
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
