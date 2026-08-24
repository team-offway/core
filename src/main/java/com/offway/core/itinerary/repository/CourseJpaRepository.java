package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.Course;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface CourseJpaRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserIdOrderByIdDesc(UUID userId);

    // 페이지 변형. 애그리거트(days·slots)는 fetch join 하지 않는다 — 컬렉션을 조인하면 페이징이
    // 메모리에서 일어난다(HHH000104). 지연 로딩은 default_batch_fetch_size 가 묶어 준다.
    Page<Course> findByUserIdOrderByIdDesc(UUID userId, Pageable pageable);

    // travelDate 가 null 인 코스는 두 조건 모두에 걸리지 않아 자연히 빠진다 — DB 마다 다른 NULL 정렬에 기대지 않는다.
    /**
     * 그 날짜에 떠나는 코스 전부(소유자 무관) — 알림 배치용(#269).
     *
     * <p>{@code UserIdIsNotNull} 로 소유자 없는 코스(공유 링크용, #261)를 뺀다. 알릴 상대가 없는 코스다.
     */
    List<Course> findByTravelDateAndUserIdIsNotNull(LocalDate travelDate);

    /**
     * 시작일이 이 구간에 든 코스 전부(소유자 무관) — 종료일 기준 알림 배치용(#302).
     *
     * <p><b>종료일로 직접 거르지 않는 이유.</b> 종료일은 컬럼이 아니라
     * {@code travel_date + travel_days - 1} 로 계산되는 값이라, DB 에서 비교하려면 방언에 묶인 날짜 연산을
     * 질의에 박아야 한다. 대신 시작일 범위로 후보를 좁혀 오고 정확한 판정은 도메인({@code travelEndDate()})이
     * 한다 — 계산 규칙이 한 곳에만 있게 된다.
     *
     * <p>범위 폭은 코스 최대 기간({@code Course.MAX_TRAVEL_DAYS})이라 후보가 며칠치를 넘지 않는다.
     */
    List<Course> findByTravelDateBetweenAndUserIdIsNotNull(LocalDate from, LocalDate to);

    List<Course> findByUserIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
            UUID userId, LocalDate today);

    Page<Course> findByUserIdAndTravelDateGreaterThanEqualOrderByTravelDateAscIdDesc(
            UUID userId, LocalDate today, Pageable pageable);

    List<Course> findByUserIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(UUID userId, LocalDate today);

    Page<Course> findByUserIdAndTravelDateLessThanOrderByTravelDateDescIdDesc(
            UUID userId, LocalDate today, Pageable pageable);

    Optional<Course> findByIdAndUserId(Long id, UUID userId);

    int deleteByUserId(UUID userId);
}
