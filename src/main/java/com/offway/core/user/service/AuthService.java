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
     *
     * <p><b>로그인은 더 이상 기기를 잇지 않는다(#280).</b> 코스·연차의 소유 키가 {@code user_id} 로 옮겨가,
     * 로그인 시점에 게스트 키를 이어 둘 이유가 사라졌다. 그 연결은 검증 불가능한 헤더 값으로 만들어져
     * 오히려 남의 데이터를 자기 계정에 붙일 수 있는 통로였다.
     */
    public IssuedToken login(SocialLoginCommand command) {
        SocialIdentity identity = socialIdentityResolver.resolve(command.provider(), command.credential());
        return issueTokens(findOrCreateUser(identity, command));
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
            case TokenRotation.Raced ignored -> {
                // 세션을 끊지 않는다 — 이긴 요청이 방금 받아 간 정상 토큰까지 죽으면 사용자가 멀쩡한 토큰을
                // 들고 로그아웃된다. 이 요청만 거절하고 클라이언트가 새 토큰으로 다시 오게 둔다.
                log.info("회전 직후 같은 refresh 가 다시 왔습니다 — 재시도로 보고 이 요청만 거절합니다");
                throw UserException.invalidRefreshToken();
            }
            case TokenRotation.Invalid ignored -> throw UserException.invalidRefreshToken();
        };
    }

    public void logout(UUID userId) {
        // Basic 으로 들어온 요청은 principal 이 UUID 가 아니라 null 로 온다(@LoginUser 가 JWT 가 넣은 것만 푼다).
        // 그대로 두면 폐기할 대상이 없는데 200 이 나가, 클라이언트는 로그아웃됐다고 믿고 토큰은 살아 있다 —
        // 규약이 막는 '조용한 실패' 다. 애초에 Basic 은 앱의 로그인 수단이 아니므로 401 로 끊는다.
        if (userId == null) {
            log.info("로그아웃 요청에 사용자 식별자가 없습니다 — Bearer 로 온 요청이 아닙니다");
            throw UserException.invalidAccessToken();
        }
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

    /**
     * 신원으로 사용자를 찾거나 만든다 — <b>동시 가입을 흡수한다</b>.
     *
     * <p>같은 계정으로 동시에 로그인하면(로그인 버튼 더블탭이면 그대로 일어난다) 두 요청이 모두 "없다" 를
     * 읽고 둘 다 만들려 해서 하나가 {@code uk_user_identity_provider} 에 걸린다. 그대로 두면 진 쪽이 500 을
     * 받는데, 사용자가 원한 상태(계정이 하나 있다)는 이미 이뤄져 있다.
     *
     * <p>먼저 만든 쪽의 사용자를 다시 읽어 돌려준다. <b>그때 {@code isNewUser} 는 false 다</b> — 이 요청이
     * 만든 것이 아니고, 진 쪽에도 true 를 주면 앱이 온보딩을 두 번 띄운다.
     *
     * <p>다시 읽어도 없으면 중복이 아닌 다른 제약 위반이다. 그건 삼키지 않는다 — 확인 없이 넘기면 계정이
     * 없는데 로그인에 성공한 것처럼 보인다.
     */
    private AuthenticatedUser findOrCreateUser(SocialIdentity identity, SocialLoginCommand command) {
        try {
            return userPersistenceService.findOrCreateUser(identity, command.nickname(), command.email());
        } catch (RuntimeException e) {
            return userPersistenceService
                    .findExistingUser(identity)
                    .map(existing -> {
                        log.info("가입 경합 — 먼저 만들어진 계정을 그대로 씁니다 provider={}", identity.provider());
                        return existing;
                    })
                    .orElseThrow(() -> e);
        }
    }
}
