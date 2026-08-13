package com.offway.core.transport.repository;

import com.offway.core.transport.domain.FerryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class FerryPortRepositoryImpl implements FerryPortRepository {

    private final FerryPortJpaRepository jpa;

    @Override
    public List<FerryPort> findAll() {
        return jpa.findAll();
    }
}
