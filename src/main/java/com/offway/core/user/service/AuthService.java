package com.offway.core.user.service;

import com.offway.core.user.domain.AuthProvider;
import java.util.List;
import java.util.Optional;
import com.offway.core.user.infrastructure.apple.AppleAccountLink;
import com.offway.core.common.logging.RootCause;
import com.offway.core.leave.domain.LeaveBalance;
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
    private final AppleAccountLink appleAccountLink;
    private final TokenIssuer tokenIssuer;

    /**
     * provider 토큰으로 신원을 확인해 로그인시킨다. 처음 보는 신원이면 그대로 가입 처리된다.
     *
     * <p>확인(외부 호출일 수 있다)을 먼저 끝내고 DB 작업만 위임하는 순서를 지킨다. Kakao 는 프로필 조회가 끼는데,
     * 그것이 트랜잭션 안에 들어가면 read-timeout 동안 DB 커넥션을 잡아 풀이 마른다.
     */
    public IssuedToken login(SocialLoginCommand command) {
        SocialIdentity identity = socialIdentityResolver.resolve(command.provider(), command.credential());
        AuthenticatedUser user = findOrCreateUser(identity, command);
        linkDevice(user.userId(), command.guestId());
        rememberProviderLink(user.userId(), identity, command.authorizationCode());
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

    /**
     * 탈퇴 때 provider 연결을 끊을 수 있게 갱신 토큰을 받아 둔다(#287) — <b>실패해도 로그인을 막지 않는다</b>.
     *
     * <p><b>지금 해야만 한다.</b> {@code authorizationCode} 는 1회용·5분이라 탈퇴 시점에는 이미 없다.
     * 로그인 그 순간이 유일한 기회다.
     *
     * <p>그런데도 <b>로그인의 조건은 아니다.</b> Apple 토큰 엔드포인트가 흔들릴 때 여기서 던지면 로그인 자체가
     * 막힌다 — 연결이 남는 것보다 훨씬 나쁘다. 못 받으면 흔적만 남기고 넘어가고, 그 사용자가 다시 로그인하면
     * 그때 채워진다.
     *
     * <p><b>어느 클라이언트로 발급됐는지는 검증된 {@code aud} 가 안다.</b> 네이티브(Bundle ID)와 웹(Service ID)이
     * 갈리는데, 그 값이 방금 서명을 확인한 ID 토큰에 들어 있다. 그걸로 <b>한 번만</b> 교환한다.
     *
     * <p>후보를 순서대로 시도하지 않는 이유는 {@code authorizationCode} 가 <b>1회용</b>이라는 것이다. 첫 시도가
     * 틀린 클라이언트였을 때 Apple 이 코드를 살려 둔다는 보장이 없다 — 그러면 맞는 클라이언트로 다시 시도해도
     * 이미 늦고, 갱신 토큰을 영영 못 받아 <b>탈퇴해도 Apple 연결이 남는다</b>. 이 PR 이 하려는 일이 정확히
     * 그것이라 추측으로 한 번을 낭비할 수 없다.
     *
     * <p>{@code aud} 가 없으면(옛 토큰·검증 경로가 아닌 provider) 예전처럼 후보를 돈다. 아무것도 안 하는 것보다는
     * 낫고, 그 경우에만 코드 소진 위험을 감수한다.
     */
    private void rememberProviderLink(UUID userId, SocialIdentity identity, String authorizationCode) {
        AuthProvider provider = identity.provider();
        if (provider != AuthProvider.APPLE || authorizationCode == null || authorizationCode.isBlank()) {
            return;
        }
        for (String clientId : candidateClientIds(identity)) {
            Optional<String> refreshToken = appleAccountLink.exchange(authorizationCode, clientId);
            if (refreshToken.isPresent()) {
                try {
                    userPersistenceService.rememberProviderToken(
                            userId, provider, refreshToken.get(), clientId);
                } catch (RuntimeException e) {
                    // 저장 실패가 로그인을 막지 않는다 — linkDevice 와 같은 판단이다.
                    log.warn("Apple 갱신 토큰을 저장하지 못했습니다 userId={} cause={}", userId, RootCause.label(e));
                }
                return;
            }
        }
        // 사유를 남긴다 — 이 로그가 쌓이면 탈퇴해도 Apple 연결이 계속 남는다는 뜻이다.
        log.warn("Apple 갱신 토큰을 받지 못했습니다 — 탈퇴해도 Apple 연결이 남습니다 userId={}", userId);
    }

    /**
     * 교환에 쓸 클라이언트 — <b>검증된 {@code aud} 가 있으면 그것 하나뿐</b>이다.
     *
     * <p>{@code aud} 를 우리가 설정한 후보 안에서만 받는다. 검증기가 이미 같은 확인을 하지만, 여기서 한 번 더
     * 거르는 것은 이 값이 <b>서명 키를 고르는 데 쓰이기</b> 때문이다 — 설정에 없는 클라이언트로 서명할 이유가 없다.
     */
    private List<String> candidateClientIds(SocialIdentity identity) {
        List<String> configured = appleAccountLink.clientIds();
        return identity.audienceIfPresent()
                .filter(configured::contains)
                .map(List::of)
                .orElse(configured);
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
     * 이 기기를 사용자에게 이어 둔다(#34) — <b>실패해도 로그인을 막지 않는다</b>.
     *
     * <p>코스·연차가 아직 {@code guest_id} 로 묶여 있어, 서버가 "이 사용자의 데이터가 무엇인가" 를 알 수 있는
     * 유일한 근거다. 탈퇴가 이것으로 대상을 찾는다. 다만 <b>기록은 나중을 위한 것이지 로그인의 조건이 아니다</b> —
     * 여기서 터지면 계정은 만들어졌는데 토큰을 못 받아, 재시도해도 같은 자리에서 계속 실패하는 락아웃이 된다.
     *
     * <p>그래서 두 가지를 트랜잭션 <b>밖</b>에서 처리한다.
     *
     * <ul>
     *   <li><b>형식</b> — 헤더는 아무나 아무 값이나 보낼 수 있다. 길이를 넘기면 DB 가 잘라내며 터지는데,
     *       그 값으로는 코스·연차도 저장되지 않으므로 이어 둘 이유 자체가 없다. 넘어가고 흔적만 남긴다.
     *   <li><b>경합</b> — 유니크 제약 위반은 그 트랜잭션을 rollback-only 로 만들어 <b>안에서 삼켜도 소용없다</b>.
     *       커밋에서 {@code UnexpectedRollbackException} 으로 끝난다. 밖에서 잡아야 한다.
     * </ul>
     */
    private void linkDevice(UUID userId, String guestId) {
        if (guestId == null || guestId.isBlank()) {
            log.info("기기 식별자 없이 로그인했습니다 — 이 사용자의 코스·연차는 이어지지 않습니다 userId={}", userId);
            return;
        }
        if (guestId.length() > LeaveBalance.MAX_OWNER_ID_LENGTH) {
            log.warn("기기 식별자가 너무 깁니다 — 이어 두지 않습니다 userId={} length={}", userId, guestId.length());
            return;
        }
        try {
            userPersistenceService.linkGuest(userId, guestId, Instant.now());
        } catch (RuntimeException e) {
            // **좁게 잡지 않는다.** 제약 위반은 그 트랜잭션을 rollback-only 로 만들어, 커밋 시점에
            // DataIntegrityViolationException 이 아니라 UnexpectedRollbackException 으로 올라온다.
            // 타입을 골라 잡으면 그중 하나가 새어 나가 로그인 전체가 500 이 되고, 계정은 만들어졌는데
            // 토큰을 못 받아 재시도해도 같은 자리에서 실패하는 락아웃이 된다.
            //
            // 무엇으로 실패했는지는 남긴다 — 경합이면 정상이고(먼저 넣은 쪽이 이겼다) 그 밖이면 봐야 한다.
            log.warn("기기를 잇지 못했습니다 — 로그인은 계속합니다 userId={} cause={}", userId, RootCause.label(e));
        }
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
