package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface PolicyJpaRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByVerifiedTrue();

    /** 같은 분류의 노출 대상 — 뱃지가 겹치는지 보려고 읽는다(#344). */
    List<Policy> findByTypeAndVerifiedTrue(PolicyType type);

    /**
     * 같은 조회를 <b>잠그고</b> 읽는다(#391).
     *
     * <p>{@code idx_policy_type} 을 타는 범위 스캔이라 InnoDB 가 그 분류 구간에 next-key 잠금을
     * 건다. <b>행이 하나도 없을 때도</b> 그 자리(gap)를 잠그므로, 같은 분류의 첫 정책 둘이 동시에
     * 들어오는 <b>가장 위험한 경우</b>가 막힌다 — 둘 다 "중복 없음" 을 읽고 둘 다 저장하는 상황이다.
     *
     * <p>별도 잠금 표를 두지 않은 이유는 <b>이 표가 작기 때문</b>이다. 분류가 일곱이고 행은 그 남짓
     * 이라, 잠금이 넓어져도 치를 값이 없다. 어드민만 쓰는 경로라 처리량 걱정도 없다.
     *
     * <p>교착이 없다. 한 문장이 한 분류 구간만 잠그고 두 트랜잭션이 같은 순서로 같은 잠금을
     * 기다리므로, 한쪽이 그냥 기다린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Policy> findWithLockByTypeAndVerifiedTrue(PolicyType type);

    /**
     * 지운 행 수를 돌려주는 삭제(#344) — <b>확인과 삭제를 한 문장으로</b> 하려는 것이다.
     *
     * <p>미리 조회해 확인하면 그 사이에 다른 어드민이 같은 정책을 지웠을 때 404 여야 할 요청이 200 으로
     * 나간다. {@code CuratedLinkJpaRepository} 와 같은 판단이다.
     *
     * <p>{@code clearAutomatically} 가 필요하다. 벌크 삭제는 영속성 컨텍스트를 지나치므로, 지우기 전에
     * 읽어 둔 엔티티가 1차 캐시에 남아 <b>지운 뒤 조회가 그 값을 그대로 돌려준다.</b>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Policy policy where policy.id = :id")
    int deleteByIdReturningCount(@Param("id") long id);
}
