package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface PolicyJpaRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByVerifiedTrue();
}
