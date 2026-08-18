package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveBalance;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class LeaveBalanceRepositoryImpl implements LeaveBalanceRepository {

    private final LeaveBalanceJpaRepository jpaRepository;

    @Override
    public Optional<LeaveBalance> findByGuestId(String guestId) {
        return jpaRepository.findByGuestId(guestId);
    }

    @Override
    public LeaveBalance save(LeaveBalance balance) {
        return jpaRepository.save(balance);
    }

    @Override
    public int deleteByGuestId(String guestId) {
        return jpaRepository.deleteByGuestId(guestId);
    }
}
