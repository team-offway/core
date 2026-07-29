package com.offway.core.user.service;

import com.offway.core.user.domain.OidcUser;
import com.offway.core.user.domain.RefreshToken;
import com.offway.core.user.domain.User;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.repository.RefreshTokenRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserRepository;
import com.offway.core.user.service.dto.TokenRotation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증의 영속 경계.
 *
 * <p>{@link AuthService} 가 외부 호출(provider JWKS)을 트랜잭션 밖에서 끝낸 뒤 DB 작업만 이 빈에 위임한다 —
 * 외부 read-timeout 이 DB 커넥션을 오래 잡지 않게(persistence-convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersistenceService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 검증된 provider 신원으로 사용자를 찾거나 만든다. 최초 로그인이 곧 가입이다.
     *
     * <p>닉네임은 요청 값 → ID 토큰 클레임 순으로 채운다. Apple 은 토큰에 이름을 주지 않아 요청 값이 유일한 출처다.
     */
    @Transactional
    public UUID findOrCreateUser(OidcUser oidcUser, String requestedNickname) {
        return userIdentityRepository
                .findByProviderAndSubject(oidcUser.provider(), oidcUser.subject())
                .map(UserIdentity::getUserId)
                .orElseGet(() -> register(oidcUser, requestedNickname));
    }

    /** local 개발 로그인용 — provider 연결 없이 사용자만 만든다. */
    @Transactional
    public UUID createUser(String nickname) {
        return userRepository.save(User.withNickname(nickname)).getId();
    }

    @Transactional
    public void saveRefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        refreshTokenRepository.save(RefreshToken.issue(userId, tokenHash, expiresAt));
    }

    /**
     * refresh 토큰 회전을 시도하고 결과를 돌려준다.
     *
     * <p>실패를 예외가 아니라 {@link TokenRotation} 으로 돌려준다. 재사용 감지 시 해야 할 "사용자 토큰 전체 폐기"를
     * 이 트랜잭션 안에서 하고 예외를 던지면 그 폐기까지 롤백돼, 탈취된 토큰이 그대로 살아남기 때문이다. 후속 조치는
     * 호출자가 별도 트랜잭션으로 끝낸다.
     */
    @Transactional
    public TokenRotation rotateRefreshToken(String currentHash, String nextHash, Instant nextExpiry, Instant now) {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(currentHash);
        if (found.isEmpty()) {
            return new TokenRotation.Invalid();
        }
        RefreshToken current = found.get();
        if (current.isRevoked()) {
            return new TokenRotation.Reused(current.getUserId());
        }
        if (current.isExpired(now)) {
            return new TokenRotation.Invalid();
        }
        current.revoke(now);
        refreshTokenRepository.save(RefreshToken.issue(current.getUserId(), nextHash, nextExpiry));
        return new TokenRotation.Rotated(current.getUserId());
    }

    /** 로그아웃 — 살아 있는 refresh 를 모두 폐기한다. access 는 만료까지 유효하다(무상태 JWT 의 대가). */
    @Transactional
    public void revokeAllRefreshTokens(UUID userId, Instant now) {
        revokeActive(userId, now);
    }

    private UUID register(OidcUser oidcUser, String requestedNickname) {
        String nickname = requestedNickname != null && !requestedNickname.isBlank()
                ? requestedNickname
                : oidcUser.nicknameIfPresent().orElse(null);
        User user = userRepository.save(User.withNickname(nickname));
        userIdentityRepository.save(UserIdentity.link(user.getId(), oidcUser.provider(), oidcUser.subject()));
        log.info("신규 가입 provider={} userId={}", oidcUser.provider(), user.getId());
        return user.getId();
    }

    /** 트랜잭션 안에서만 호출된다 — 관리 상태 엔티티라 dirty checking 으로 반영된다. self-invocation 을 피하려 private. */
    private void revokeActive(UUID userId, Instant now) {
        refreshTokenRepository.findActiveByUserId(userId).forEach(token -> token.revoke(now));
    }
}
