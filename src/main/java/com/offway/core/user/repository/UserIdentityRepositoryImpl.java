package com.offway.core.user.repository;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class UserIdentityRepositoryImpl implements UserIdentityRepository {

    private final UserIdentityJpaRepository userIdentityJpaRepository;

    @Override
    public UserIdentity save(UserIdentity identity) {
        return userIdentityJpaRepository.save(identity);
    }

    @Override
    public Optional<UserIdentity> findByProviderAndSubject(AuthProvider provider, String subject) {
        return userIdentityJpaRepository.findByProviderAndProviderUserId(provider, subject);
    }

    @Override
    public Optional<UserIdentity> findFirstByUserId(UUID userId) {
        return userIdentityJpaRepository.findFirstByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    @Override
    public Optional<UserIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider) {
        return userIdentityJpaRepository.findByUserIdAndProvider(userId, provider);
    }

    @Override
    public int deleteByUserId(UUID userId) {
        return userIdentityJpaRepository.deleteByUserId(userId);
    }
}
