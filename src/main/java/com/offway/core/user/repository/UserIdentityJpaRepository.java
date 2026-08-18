package com.offway.core.user.repository;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface UserIdentityJpaRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    int deleteByUserId(UUID userId);
}
