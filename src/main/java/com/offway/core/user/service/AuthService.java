package com.offway.core.user.service;

import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityResolver;
import com.offway.core.user.service.dto.AuthenticatedUser;
import com.offway.core.user.service.dto.IssuedToken;
import com.offway.core.user.service.dto.SocialLoginCommand;
import com.offway.core.user.service.dto.TokenRotation;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로그인·재발급·로그아웃 조율.
 *
 * <p>provider 신원 확인(Kakao 프로필 조회 · JWKS 갱신)은 외부 호출이라 트랜잭션 밖에서 끝내고, DB 작업만
 * {@link UserPersistenceService} 에 위임한다. 그래서 이 클래스에는 {@code @Transactional} 이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SocialIdentityResolver socialIdentityResolver;
    private final UserPersistenceService userPersistenceService;
    private final TokenIssuer tokenIssuer;

    /**
     * provider 토큰으로 신원을 확인해 로그인시킨다. 처음 보는 신원이면 그대로 가입 처리된다.
     *
     * <p>확인(외부 호출일 수 있다)을 먼저 끝내고 DB 작업만 위임하는 순서를 지킨다. Kakao 는 프로필 조회가 끼는데,
     * 그것이 트랜잭션 안에 들어가면 read-timeout 동안 DB 커넥션을 잡아 풀이 마른다.
     */
    public IssuedToken login(SocialLoginCommand command) {
        SocialIdentity identity = socialIdentityResolver.resolve(command.provider(), command.credential());
        AuthenticatedUser user =
                userPersistenceService.findOrCreateUser(identity, command.nickname(), command.email());
        return issueTokens(user);
    }

    /**
     * refresh 토큰을 회전시켜 새 토큰 쌍을 발급한다.
     *
     * <p>이미 회전된 토큰이 다시 오면 탈취로 보고 이 사용자의 토큰을 전부 끊는다. 폐기는 회전 트랜잭션과 분리해야 한다 —
     * 같은 트랜잭션에서 폐기하고 예외를 던지면 그 폐기까지 롤백돼 탈취된 토큰이 살아남는다.
     */
    public IssuedToken reissue(String refreshToken) {
        Instant now = Instant.now();
        String nextRefreshToken = tokenIssuer.generateRefreshToken();
        TokenRotation rotation = userPersistenceService.rotateRefreshToken(
                tokenIssuer.hashRefreshToken(refreshToken),
                tokenIssuer.hashRefreshToken(nextRefreshToken),
                tokenIssuer.refreshTokenExpiry(now),
                now);
        return switch (rotation) {
            case TokenRotation.Rotated(UUID userId) -> new IssuedToken(
                    tokenIssuer.issueAccessToken(userId),
                    nextRefreshToken,
                    tokenIssuer.accessTokenSeconds(),
                    false);
            case TokenRotation.Reused(UUID userId) -> {
                log.warn("폐기된 refresh 토큰 재사용 — 사용자 토큰 전체 폐기 userId={}", userId);
                userPersistenceService.revokeAllRefreshTokens(userId, now);
                throw UserException.invalidRefreshToken();
            }
            case TokenRotation.Invalid ignored -> throw UserException.invalidRefreshToken();
        };
    }

    public void logout(UUID userId) {
        userPersistenceService.revokeAllRefreshTokens(userId, Instant.now());
    }

    /**
     * local 전용 개발 로그인 — provider 검증 없이 사용자를 만들고 토큰을 발급한다.
     *
     * <p>호출자는 {@code DevAuthController}({@code @Profile("local")}) 뿐이다. prod 에는 그 빈이 존재하지 않아
     * 경로 자체가 열리지 않는다.
     */
    public IssuedToken devLogin(String nickname) {
        return issueTokens(new AuthenticatedUser(userPersistenceService.createUser(nickname), true));
    }

    private IssuedToken issueTokens(AuthenticatedUser user) {
        Instant now = Instant.now();
        String refreshToken = tokenIssuer.generateRefreshToken();
        userPersistenceService.saveRefreshToken(
                user.userId(), tokenIssuer.hashRefreshToken(refreshToken), tokenIssuer.refreshTokenExpiry(now));
        return new IssuedToken(
                tokenIssuer.issueAccessToken(user.userId()),
                refreshToken,
                tokenIssuer.accessTokenSeconds(),
                user.newUser());
    }
}
