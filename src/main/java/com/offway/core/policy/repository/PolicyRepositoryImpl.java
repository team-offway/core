package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Override
    public List<Policy> findAll() {
        return policyJpaRepository.findAll();
    }

    @Override
    public Page<Policy> findAll(Pageable pageable) {
        return policyJpaRepository.findAll(pageable);
    }

    @Override
    public Policy save(Policy policy) {
        return policyJpaRepository.save(policy);
    }

    @Override
    public int deleteById(long id) {
        return policyJpaRepository.deleteByIdReturningCount(id);
    }

    @Override
    public List<Policy> findVerifiedByType(PolicyType type) {
        return policyJpaRepository.findByTypeAndVerifiedTrue(type);
    }

    @Override
    public List<Policy> findVerifiedByTypeForUpdate(PolicyType type) {
        return policyJpaRepository.findWithLockByTypeAndVerifiedTrue(type);
    }
}
