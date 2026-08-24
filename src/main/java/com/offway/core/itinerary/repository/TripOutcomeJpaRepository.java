package com.offway.core.itinerary.repository;

import com.offway.core.itinerary.domain.TripOutcome;
import java.util.Collection;
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

    /**
     * 이 코스들 중 <b>이미 답한 것</b>(소유자 무관) — 알림 배치용(#302).
     *
     * <p>배치는 소유자별로 돌지 않는다. 소유자마다 한 번씩 물으면 대상 수만큼 질의가 나가고, 그게 곧
     * N+1 이다. 코스 id 를 모아 한 번에 묻는다.
     */
    @Query("SELECT o.courseId FROM TripOutcome o WHERE o.courseId IN :courseIds")
    Set<Long> findAnsweredCourseIdsIn(@Param("courseIds") Collection<Long> courseIds);

    int deleteByUserId(UUID userId);
}
