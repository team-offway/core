package com.offway.core.leave.repository;

import com.offway.core.leave.domain.LeaveBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface LeaveBalanceJpaRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByUserId(UUID userId);

    int deleteByUserId(UUID userId);
}
