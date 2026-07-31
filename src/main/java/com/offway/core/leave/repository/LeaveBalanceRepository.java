package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveBalance;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link LeaveBalanceRepositoryImpl}. */
public interface LeaveBalanceRepository {

    Optional<LeaveBalance> findByGuestId(String guestId);

    LeaveBalance save(LeaveBalance balance);
}
