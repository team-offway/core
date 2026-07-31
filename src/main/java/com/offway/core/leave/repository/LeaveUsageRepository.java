package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;
import java.util.Set;

/** 도메인이 의존하는 port. 구현은 {@link LeaveUsageRepositoryImpl}. */
public interface LeaveUsageRepository {

    /** 최근 사용부터. 화면이 내역을 역순으로 보여준다. */
    List<LeaveUsage> findByGuestId(String guestId);

    /** 증감 합 — 쓴 연차. 내역이 없으면 0. */
    double sumDaysByGuestId(String guestId);

    /** 이 코스로 이미 쌓인 내역이 있는가 — 중복 차감 방지(#91). */
    boolean existsByGuestIdAndCourseId(String guestId, Long courseId);

    /**
     * 이 소유자가 코스로 차감한 코스 ID 들 — 목록 화면의 "차감함" 표시용.
     *
     * <p>코스마다 {@link #existsByGuestIdAndCourseId} 를 부르면 N+1 이라 한 번에 모아온다.
     */
    Set<Long> findDeductedCourseIds(String guestId);

    /**
     * 코스 차감 내역을 지운다(차감 취소).
     *
     * <p>음수 행을 덧붙이지 않는 이유 — {@code uk_leave_usage_guest_course} 가 코스당 한 행을 강제한다(#91).
     * 음수 누적은 수동 내역 전용이다.
     *
     * @return 지운 행 수 (없었으면 0)
     */
    int deleteByGuestIdAndCourseId(String guestId, Long courseId);

    LeaveUsage save(LeaveUsage usage);
}
