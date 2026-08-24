package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveBalance;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class LeaveBalanceRepositoryImpl implements LeaveBalanceRepository {

    private final LeaveBalanceJpaRepository jpaRepository;

    @Override
    public Optional<LeaveBalance> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public LeaveBalance save(LeaveBalance balance) {
        return jpaRepository.save(balance);
    }

    @Override
    public int deleteByUserId(UUID userId) {
        return jpaRepository.deleteByUserId(userId);
    }
}
