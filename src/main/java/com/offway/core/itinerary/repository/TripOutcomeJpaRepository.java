package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.TripOutcome;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface TripOutcomeJpaRepository extends JpaRepository<TripOutcome, Long> {

    /** 코스 ID 만 뽑는다 — 대기 목록은 "답했는가" 만 알면 되므로 행 전체를 끌어올 이유가 없다. */
    @Query("SELECT o.courseId FROM TripOutcome o WHERE o.userId = :userId")
    Set<Long> findAnsweredCourseIds(@Param("userId") UUID userId);

    int deleteByUserId(UUID userId);
}
