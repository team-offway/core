package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import com.offway.core.user.domain.RevokedReason;
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
    public int deleteByUserId(UUID userId) {
        return refreshTokenJpaRepository.deleteByUserId(userId);
    }

    @Override
    public int claimRotation(String tokenHash, Instant now) {
        return refreshTokenJpaRepository.claimRotation(tokenHash, now, RevokedReason.ROTATED);
    }

    @Override
    public int revokeActive(UUID userId, Instant now, RevokedReason reason) {
        return refreshTokenJpaRepository.revokeActiveByUserId(userId, now, reason);
    }

    @Override
    public int revokeOne(UUID userId, String tokenHash, Instant now) {
        return refreshTokenJpaRepository.revokeOneByUserIdAndTokenHash(
                userId, tokenHash, now, RevokedReason.LOGOUT);
    }
}
