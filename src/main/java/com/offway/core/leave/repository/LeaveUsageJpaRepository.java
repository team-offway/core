package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface LeaveUsageJpaRepository extends JpaRepository<LeaveUsage, Long> {

    /**
     * 소유자의 사용 내역 — <b>등록한 순서</b>로(#375).
     *
     * <p>예전에는 사용일 내림차순이었다. 그러면 미래 날짜로 미리 잡아 둔 내역이 늘 맨 위에 붙어, 방금
     * 등록한 것이 아래로 숨는다 — 목록에서 찾는 것은 "방금 한 일" 인데 그게 안 보였다. 그래서 앱이 받아서
     * id 로 다시 정렬하고 있었다.
     *
     * <p>{@code id} 를 보조 키로 남긴다. 이 컬럼이 생기기 전 행은 {@code created_at} 이 null 이고,
     * MySQL 은 {@code DESC} 에서 null 을 뒤로 보내므로 옛 행끼리는 이 키로 갈린다.
     */
    List<LeaveUsage> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    /** 합을 DB 에서 낸다 — 내역을 전부 끌어와 자바로 더하면 내역이 쌓일수록 느려진다. 없으면 null 이라 0 으로 감싼다. */
    @Query("SELECT COALESCE(SUM(u.days), 0) FROM LeaveUsage u WHERE u.userId = :userId")
    double sumDaysByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndCourseId(UUID userId, Long courseId);

    Optional<LeaveUsage> findByUserIdAndCourseId(UUID userId, Long courseId);

    /** 소유자를 조건에 함께 건다 — 남의 내역은 애초에 읽히지 않는다(#265). */
    Optional<LeaveUsage> findByIdAndUserId(Long id, UUID userId);

    /** 코스 ID 만 뽑는다 — 목록 화면은 "차감했는가" 만 알면 되므로 내역 전체를 끌어올 이유가 없다. */
    @Query("SELECT u.courseId FROM LeaveUsage u WHERE u.userId = :userId AND u.courseId IS NOT NULL")
    Set<Long> findDeductedCourseIds(@Param("userId") UUID userId);

    /**
     * 이 코스들 중 <b>이미 차감한 것</b>(소유자 무관) — 알림 배치용(#302).
     *
     * <p>소유자를 안 거는 것이 여기서는 맞다. 코스 id 는 이미 "그 소유자의 코스" 로 좁혀져 넘어오고,
     * 배치가 소유자마다 한 번씩 물으면 대상 수만큼 질의가 나간다.
     */
    @Query("SELECT u.courseId FROM LeaveUsage u WHERE u.courseId IN :courseIds")
    Set<Long> findDeductedCourseIdsIn(@Param("courseIds") Collection<Long> courseIds);

    /** @return 지운 행 수 */
    int deleteByUserIdAndCourseId(UUID userId, Long courseId);

    int deleteByUserId(UUID userId);
}
