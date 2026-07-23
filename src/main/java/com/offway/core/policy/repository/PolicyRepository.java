package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import java.util.List;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link PolicyRepositoryImpl}. */
public interface PolicyRepository {

    Optional<Policy> findById(Long id);

    /** 상세·기간까지 확정(verified)된 정책만. 데모/노출 대상. */
    List<Policy> findAllVerified();
}
