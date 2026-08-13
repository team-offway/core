package com.offway.core.transport.repository;

import com.offway.core.transport.domain.BusTerminal;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface BusTerminalJpaRepository extends JpaRepository<BusTerminal, Long> {
}
