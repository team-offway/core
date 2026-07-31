package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;

/** 도메인이 의존하는 port. 구현은 {@link LeaveUsageRepositoryImpl}. */
public interface LeaveUsageRepository {

    /** 최근 사용부터. 화면이 내역을 역순으로 보여준다. */
    List<LeaveUsage> findByGuestId(String guestId);

    /** 증감 합 — 쓴 연차. 내역이 없으면 0. */
    double sumDaysByGuestId(String guestId);

    /** 이 코스로 이미 쌓인 내역이 있는가 — 중복 차감 방지(#91). */
    boolean existsByGuestIdAndCourseId(String guestId, Long courseId);

    LeaveUsage save(LeaveUsage usage);
}
