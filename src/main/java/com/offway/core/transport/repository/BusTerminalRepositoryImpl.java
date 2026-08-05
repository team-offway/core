package com.offway.core.transport.repository;

import com.offway.core.transport.domain.BusTerminal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class BusTerminalRepositoryImpl implements BusTerminalRepository {

    private final BusTerminalJpaRepository jpa;

    @Override
    public List<BusTerminal> findAll() {
        return jpa.findAll();
    }
}
