package com.offway.core.policy.repository;

import com.offway.core.policy.domain.Policy;
import com.offway.core.policy.domain.PolicyType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface PolicyJpaRepository extends JpaRepository<Policy, Long> {

    List<Policy> findByVerifiedTrue();

    /** 같은 분류의 노출 대상 — 뱃지가 겹치는지 보려고 읽는다(#344). */
    List<Policy> findByTypeAndVerifiedTrue(PolicyType type);

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
