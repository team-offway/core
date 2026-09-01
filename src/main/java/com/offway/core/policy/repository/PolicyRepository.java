package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import java.util.List;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link PolicyRepositoryImpl}. */
public interface PolicyRepository {

    Optional<Policy> findById(Long id);

    /** 상세·기간까지 확정(verified)된 정책만. 데모/노출 대상. */
    List<Policy> findAllVerified();

    /**
     * 검증 여부와 무관하게 전부(#220).
     *
     * <p>낡음 점검은 <b>화면에 안 나가는 것까지</b> 봐야 한다 — 미검증으로 방치된 정책이 바로 그 대상이라,
     * verified 로 좁히면 알림이 잡아야 할 것을 스스로 걸러 낸다.
     */
    List<Policy> findAll();
}
