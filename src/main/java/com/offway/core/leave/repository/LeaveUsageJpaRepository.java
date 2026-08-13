package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface LeaveUsageJpaRepository extends JpaRepository<LeaveUsage, Long> {

    List<LeaveUsage> findByGuestIdOrderByUsedOnDescIdDesc(String guestId);

    /** 합을 DB 에서 낸다 — 내역을 전부 끌어와 자바로 더하면 내역이 쌓일수록 느려진다. 없으면 null 이라 0 으로 감싼다. */
    @Query("SELECT COALESCE(SUM(u.days), 0) FROM LeaveUsage u WHERE u.guestId = :guestId")
    double sumDaysByGuestId(@Param("guestId") String guestId);

    boolean existsByGuestIdAndCourseId(String guestId, Long courseId);

    Optional<LeaveUsage> findByGuestIdAndCourseId(String guestId, Long courseId);

    /** 코스 ID 만 뽑는다 — 목록 화면은 "차감했는가" 만 알면 되므로 내역 전체를 끌어올 이유가 없다. */
    @Query("SELECT u.courseId FROM LeaveUsage u WHERE u.guestId = :guestId AND u.courseId IS NOT NULL")
    Set<Long> findDeductedCourseIds(@Param("guestId") String guestId);

    /** @return 지운 행 수 */
    int deleteByGuestIdAndCourseId(String guestId, Long courseId);

    int deleteByGuestId(String guestId);
}
