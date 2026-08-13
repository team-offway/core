package com.offway.core.common.batch.repository;

import com.offway.core.common.batch.domain.BatchRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data — {@link BatchRunRepositoryImpl} 이 위임한다. */
public interface BatchRunJpaRepository extends JpaRepository<BatchRun, Long> {

    Optional<BatchRun> findByName(String name);
}
