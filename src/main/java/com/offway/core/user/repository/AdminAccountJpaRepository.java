package com.offway.core.user.repository;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface AdminAccountJpaRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
