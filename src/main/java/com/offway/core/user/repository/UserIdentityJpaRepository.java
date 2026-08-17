package com.offway.core.user.repository;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface UserIdentityJpaRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
     * 이 사용자의 신원 하나 — <b>가장 먼저 만들어진 것</b>.
     *
     * <p>{@code findByUserId} 로 두지 않는다. 한 사용자에게 신원이 둘 이상 생기면 {@code Optional} 반환이
     * {@code IncorrectResultSizeDataAccessException}(500)이 되는데, 그건 조회 하나가 데이터 모양 때문에
     * 통째로 죽는 것이다. 정렬을 못박아 두면 그때도 결정적으로 하나를 고른다.
     */
    Optional<UserIdentity> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);

    int deleteByUserId(UUID userId);
}
