package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveBalance;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link LeaveBalanceRepositoryImpl}. */
public interface LeaveBalanceRepository {

    Optional<LeaveBalance> findByGuestId(String guestId);

    LeaveBalance save(LeaveBalance balance);

    /**
     * 탈퇴 — 이 소유자의 연차 설정을 지운다.
     *
     * @return 지운 행 수 (설정한 적 없으면 0)
     */
    int deleteByGuestId(String guestId);
}
