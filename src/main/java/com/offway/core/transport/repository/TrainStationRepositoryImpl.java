package com.offway.core.transport.repository;

import com.offway.core.transport.domain.TrainStation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class TrainStationRepositoryImpl implements TrainStationRepository {

    private final TrainStationJpaRepository jpa;

    @Override
    public List<TrainStation> findAll() {
        return jpa.findAll();
    }
}
