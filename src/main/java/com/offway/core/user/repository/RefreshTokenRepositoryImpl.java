package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<RefreshToken> findActiveByUserId(UUID userId) {
        return refreshTokenJpaRepository.findByUserIdAndRevokedAtIsNull(userId);
    }

    @Override
    public int claimRotation(String tokenHash, Instant now) {
        return refreshTokenJpaRepository.claimRotation(tokenHash, now);
    }

    @Override
    public int revokeActive(UUID userId, Instant now) {
        return refreshTokenJpaRepository.revokeActiveByUserId(userId, now);
    }
}
