package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
public class PolicyRepositoryImpl implements PolicyRepository {

    private final PolicyJpaRepository policyJpaRepository;

    public PolicyRepositoryImpl(PolicyJpaRepository policyJpaRepository) {
        this.policyJpaRepository = policyJpaRepository;
    }

    @Override
    public Optional<Policy> findById(Long id) {
        return policyJpaRepository.findById(id);
    }

    @Override
    public List<Policy> findAllVerified() {
        return policyJpaRepository.findByVerifiedTrue();
    }
}
