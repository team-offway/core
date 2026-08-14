package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;
import java.util.Optional;
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
     * 이 코스의 차감 내역 — 여행 날짜를 고칠 때 차감량을 다시 계산하려고 읽는다(#170).
     *
     * <p>{@link #existsByGuestIdAndCourseId} 와 달리 <b>행 자체</b>가 필요하다. 재계산의 입력인 반차 여부가
     * 그 행에 있고, 갱신도 그 행에 한다.
     *
     * <p>{@code uk_leave_usage_guest_course} 가 코스당 한 행을 강제하므로 결과는 하나 이하다.
     */
    Optional<LeaveUsage> findByGuestIdAndCourseId(String guestId, Long courseId);

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
     * 수동 내역도 같은 규칙을 따른다 — 되돌리기는 삭제다(#265).
     *
     * @return 지운 행 수 (없었으면 0)
     */
    int deleteByGuestIdAndCourseId(String guestId, Long courseId);

    /**
     * 내 내역 한 건 — 삭제하려고 읽는다(#265).
     *
     * <p><b>소유자를 조건에 함께 건다.</b> id 로만 읽고 나중에 소유자를 비교하면, 그 비교를 빠뜨린 코드
     * 한 줄이 곧 남의 내역을 지우는 길이 된다.
     */
    Optional<LeaveUsage> findByIdAndGuestId(Long id, String guestId);

    /** 수동 내역 한 건을 지운다(#265). 코스 차감 내역인지의 판단은 도메인이 소유한다. */
    void delete(LeaveUsage usage);

    LeaveUsage save(LeaveUsage usage);
}
