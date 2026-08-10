package com.offway.core.common.batch.repository;

import com.offway.core.common.batch.domain.BatchRun;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class BatchRunRepositoryImpl implements BatchRunRepository {

    private final BatchRunJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasRunOn(String name, LocalDate date) {
        return jpaRepository.findByName(name).map(run -> run.ranOn(date)).orElse(false);
    }

    @Override
    @Transactional
    public void markStarted(String name, LocalDateTime at) {
        jpaRepository
                .findByName(name)
                .ifPresentOrElse(
                        run -> run.markStartedAt(at),
                        () -> jpaRepository.save(BatchRun.startedAt(name, at)));
    }
}
