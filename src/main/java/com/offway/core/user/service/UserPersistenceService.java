package com.offway.core.user.service;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.RefreshToken;
import com.offway.core.user.domain.User;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.repository.RefreshTokenRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserRepository;
import com.offway.core.user.service.dto.AuthenticatedUser;
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
    private final ProviderTokenCipher providerTokenCipher;

    /**
     * 검증된 provider 신원으로 사용자를 찾거나 만든다. 최초 로그인이 곧 가입이다.
     *
     * <p>가입이었는지를 {@link AuthenticatedUser#newUser()} 로 함께 돌려준다 — 앱이 온보딩과 홈을 가르는 값이라,
     * 만든 그 자리에서만 정확히 알 수 있다.
     */
    @Transactional
    public AuthenticatedUser findOrCreateUser(SocialIdentity identity, String requestedNickname, String requestedEmail) {
        return userIdentityRepository
                .findByProviderAndSubject(identity.provider(), identity.providerUserId())
                .map(found -> {
                    refreshProfileImage(found.getUserId(), identity);
                    return new AuthenticatedUser(found.getUserId(), false);
                })
                .orElseGet(() -> new AuthenticatedUser(register(identity, requestedNickname, requestedEmail), true));
    }

    /**
     * 로그인할 때마다 프로필 사진을 provider 값으로 맞춘다(#308).
     *
     * <p><b>가입 때만 저장하면 이미 가입한 사람에게는 영영 안 보인다.</b> 이 필드는 지금 추가되는 것이라
     * 기존 사용자는 전부 비어 있고, 그들이 다시 가입할 일은 없다. 사진이 바뀌거나 Kakao CDN 주소가 만료되는
     * 경우도 같은 경로로 따라간다.
     *
     * <p>값이 안 왔거나 그대로면 아무것도 하지 않는다 — {@link User#changeProfileImage} 가 그 판단을 들고
     * 있어 매 로그인마다 UPDATE 가 나가지 않는다.
     */
    private void refreshProfileImage(UUID userId, SocialIdentity identity) {
        identity.profileImageUrlIfPresent()
                .flatMap(url -> userRepository.findById(userId).map(user -> user.changeProfileImage(url)))
                .filter(Boolean::booleanValue)
                .ifPresent(changed -> log.debug("프로필 사진 갱신 userId={}", userId));
    }

    /**
     * 이미 있는 신원만 찾는다 — <b>만들지 않는다</b>.
     *
     * <p>{@link #findOrCreateUser} 가 유니크 제약에 걸렸을 때 먼저 만든 쪽의 사용자를 다시 읽으려고 있다.
     * 제약 위반은 그 트랜잭션을 rollback-only 로 만들어 같은 트랜잭션에서는 다시 읽을 수 없다 — 별도 빈의
     * 새 트랜잭션이어야 한다({@code CoursePersistenceService.findShare} 와 같은 이유).
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findExistingUser(SocialIdentity identity) {
        return userIdentityRepository
                .findByProviderAndSubject(identity.provider(), identity.providerUserId())
                .map(found -> new AuthenticatedUser(found.getUserId(), false));
    }

    /**
     * provider 가 준 갱신 토큰을 <b>암호화해</b> 신원 행에 남긴다(#287·#301).
     *
     * <p>없는 신원이면 아무것도 하지 않는다 — 로그인 직후라 정상적으로는 늘 있지만, 없다고 예외를 던지면
     * 연결 해제를 위한 저장이 로그인을 깨뜨리는 셈이 된다.
     *
     * <p><b>암호화하지 못하면 저장하지 않는다.</b> 키가 없는 환경(local)이나 암호화 실패가 그렇다. 평문으로
     * 흘려 넣으면 이 변경이 무의미해지고, 규약이 막는 조용한 실패가 된다. 결과는 "해제할 토큰이 없는
     * 사용자" 이고 그건 이미 정상 경로다 — 탈퇴는 성공하고 Apple 연결 해제만 건너뛴다.
     */
    @Transactional
    public void rememberProviderToken(UUID userId, AuthProvider provider, String refreshToken, String clientId) {
        providerTokenCipher
                .encrypt(refreshToken)
                .ifPresent(sealed -> userIdentityRepository
                        .findByUserIdAndProvider(userId, provider)
                        .ifPresent(identity -> identity.rememberProviderToken(sealed, clientId)));
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
        // 판정과 폐기를 한 문장으로 합친다. 읽고 검사하고 폐기하면 그 사이에 다른 요청이 같은 스냅샷을 읽어
        // 둘 다 회전에 성공한다 — 토큰 하나에서 살아 있는 refresh 가 둘 나오고 재사용 감지가 무의미해진다.
        boolean won = refreshTokenRepository.claimRotation(currentHash, now) > 0;
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(currentHash);
        if (found.isEmpty()) {
            return new TokenRotation.Invalid();
        }
        RefreshToken current = found.get();
        if (won) {
            refreshTokenRepository.save(RefreshToken.issue(current.getUserId(), nextHash, nextExpiry));
            return new TokenRotation.Rotated(current.getUserId());
        }
        if (current.isExpired(now)) {
            return new TokenRotation.Invalid();
        }
        // 선점에 졌다. 방금 회전된 것이면 정상 앱의 재시도·동시 요청이고, 오래전에 폐기된 것이면 탈취 정황이다.
        return current.revokedWithin(RefreshToken.ROTATION_GRACE, now)
                ? new TokenRotation.Raced()
                : new TokenRotation.Reused(current.getUserId());
    }

    /** 로그아웃 — 살아 있는 refresh 를 모두 폐기한다. access 는 만료까지 유효하다(무상태 JWT 의 대가). */
    @Transactional
    public void revokeAllRefreshTokens(UUID userId, Instant now) {
        revokeActive(userId, now);
    }

    /**
     * 새 사용자와 provider 신원을 함께 만든다.
     *
     * <p>닉네임·이메일은 <b>요청 값 → provider 가 확인해 준 값</b> 순으로 채운다. Apple 은 ID 토큰에 이름을 담지 않고
     * 최초 인증 응답에만 주므로, 그 경로에서는 요청 값이 유일한 출처다. 반대로 Kakao 는 프로필 조회로 우리가 직접
     * 받으므로 요청 값이 없어도 채워진다.
     *
     * <p>표시용 값이라 요청 값을 먼저 쓰는 것이 안전하다 — 신원(누구인가)은 요청 값을 절대 믿지 않지만
     * ({@link SocialIdentity}), 표시 이름은 틀려도 사용자가 고칠 수 있는 정보다.
     */
    private UUID register(SocialIdentity identity, String requestedNickname, String requestedEmail) {
        String nickname = firstPresent(requestedNickname, identity.nicknameIfPresent().orElse(null));
        String email = firstPresent(requestedEmail, identity.emailIfPresent().orElse(null));
        // 사진은 요청 값을 받지 않는다 — 표시 이름과 달리 앱이 보낼 이유가 없고, 남의 주소를 심을 자리가 된다.
        User user = userRepository.save(
                User.of(nickname, email, identity.profileImageUrlIfPresent().orElse(null)));
        userIdentityRepository.save(UserIdentity.link(user.getId(), identity.provider(), identity.providerUserId()));
        // 이메일·닉네임은 개인정보라 로그에 남기지 않는다. 어느 provider 로 몇 명이 들어오는지만 남긴다.
        log.info("신규 가입 provider={} userId={}", identity.provider(), user.getId());
        return user.getId();
    }

    private static String firstPresent(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    /** 트랜잭션 안에서만 호출된다 — 관리 상태 엔티티라 dirty checking 으로 반영된다. self-invocation 을 피하려 private. */
    private void revokeActive(UUID userId, Instant now) {
        // 읽어서 하나씩 고치면 행 수만큼 UPDATE 가 나간다. 이 표는 삭제 경로가 없어 계속 쌓이는 자리다.
        refreshTokenRepository.revokeActive(userId, now);
    }
}
