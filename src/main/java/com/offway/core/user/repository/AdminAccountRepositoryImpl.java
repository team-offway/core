package com.offway.core.user.repository;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class AdminAccountRepositoryImpl implements AdminAccountRepository {

    private final AdminAccountJpaRepository adminAccountJpaRepository;

    @Override
    public Optional<AdminAccount> find(AuthProvider provider, String providerUserId) {
        return adminAccountJpaRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }
}
