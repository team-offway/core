package com.offway.core.user.service;

import com.offway.core.common.logging.RootCause;
import com.offway.core.user.domain.AccountRole;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.infrastructure.apple.AppleAccountLink;
import java.util.List;
import java.util.Optional;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityResolver;
import com.offway.core.user.repository.AdminAccountRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.service.dto.AuthenticatedUser;
import com.offway.core.user.service.dto.IssuedToken;
import com.offway.core.user.service.dto.SocialLoginCommand;
import com.offway.core.user.service.dto.TokenRotation;
import java.time.Instant;
import java.util.Set;
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

    /** 로그인한 모두가 갖는 역할. */
    private static final Set<AccountRole> APP_USER_ONLY = Set.of(AccountRole.USER);

    /** 화이트리스트에 있는 계정 — <b>일반 권한을 빼앗지 않고 얹는다.</b> 어드민도 앱을 그대로 쓴다. */
    private static final Set<AccountRole> ADMIN_ROLES = Set.of(AccountRole.USER, AccountRole.ADMIN);

    private final SocialIdentityResolver socialIdentityResolver;
    private final UserPersistenceService userPersistenceService;
    private final AppleAccountLink appleAccountLink;
    private final TokenIssuer tokenIssuer;
    private final AdminAccountRepository adminAccountRepository;
    private final UserIdentityRepository userIdentityRepository;

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
        SocialIdentity identity = resolveIdentity(command);
        AuthenticatedUser user = findOrCreateUser(identity, command);
        // 기기를 잇던 단계(linkDevice)는 사라졌다(#280) — 소유가 이 사용자라 이어 둘 것이 없다.
        rememberProviderLink(user.userId(), identity, command.authorizationCode());
        // **성공도 남긴다**(#41). 실패만 찍히면 로그는 "무엇이 잘못됐나" 에만 답하고 "이 사람이 언제
        // 들어왔나" 에는 답하지 못한다 — 계정 문의가 들어왔을 때 정작 필요한 건 후자다.
        //
        // 식별자를 **전문으로** 남기는 유일한 자리다. 다른 줄은 로그 패턴이 앞 8자만 찍으므로, 그 앞자리로
        // 이 줄을 찾아오면 전체 값을 얻는다.
        log.info(
                "로그인 성공 userId={} provider={} 신규가입={}",
                user.userId(), identity.provider(), user.newUser());
        return issueTokens(user, rolesOf(identity.provider(), identity.providerUserId()));
    }

    /**
     * provider 신원을 확인한다 — <b>실패한 쪽에도 흔적을 남기고</b> 그대로 다시 던진다(#41).
     *
     * <p>여기서 실패하면 사용자 식별자가 아직 없다. 신원 확인 자체가 실패한 것이라 계정이 특정되지 않아,
     * 남는 단서는 <b>어느 provider 로 시도했는가</b>뿐이다. provider 별 검증기도 사유를 남기지만 그쪽은
     * 이 줄과 추적 id 로 묶인다.
     */
    private SocialIdentity resolveIdentity(SocialLoginCommand command) {
        try {
            return socialIdentityResolver.resolve(command.provider(), command.credential());
        } catch (RuntimeException e) {
            log.info("로그인 실패 provider={} cause={}", command.provider(), RootCause.label(e));
            throw e;
        }
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
                    // 재발급마다 화이트리스트를 다시 본다(#342). 토큰에 역할을 박아 두므로, 여기서 다시
                    // 보지 않으면 어드민에서 뺀 사람이 refresh 가 살아 있는 60일 내내 어드민으로 남는다.
                    tokenIssuer.issueAccessToken(userId, rolesOf(userId)),
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
            case TokenRotation.Invalid ignored -> {
                // 조용히 401 을 내리지 않는다(#41) — 이 갈래는 "없는 토큰·만료·이미 폐기됨" 이 전부 모이는
                // 자리라, 로그가 없으면 앱이 로그아웃 루프에 빠졌을 때 그 사실 자체가 서버에 안 보인다.
                // 토큰 원문·해시는 남기지 않는다(그것만 있으면 세션을 이어받을 수 있다).
                log.info("refresh 토큰 거절 — 없거나 만료·폐기된 토큰입니다");
                throw UserException.invalidRefreshToken();
            }
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
        // 세션을 끊은 사실을 남긴다(#41). "왜 갑자기 로그아웃됐나" 는 실제로 들어오는 문의이고, 그 답은
        // 본인이 눌렀는지(이 줄) 아니면 탈취 의심으로 우리가 끊었는지(위 Reused 줄) 로 갈린다.
        log.info("로그아웃 — 사용자 토큰 전체 폐기 userId={}", userId);
    }

    /**
     * local 전용 개발 로그인 — provider 검증 없이 사용자를 만들고 토큰을 발급한다.
     *
     * <p>호출자는 {@code DevAuthController}({@code @Profile("local")}) 뿐이다. prod 에는 그 빈이 존재하지 않아
     * 경로 자체가 열리지 않는다.
     */
    public IssuedToken devLogin(String nickname) {
        // 개발 로그인은 provider 연결을 만들지 않아 화이트리스트에 걸릴 수 없다 — 늘 일반 사용자다.
        return issueTokens(new AuthenticatedUser(userPersistenceService.createUser(nickname), true), APP_USER_ONLY);
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
                    // 저장 실패가 로그인을 막지 않는다. 이 값이 없으면 탈퇴 때 Apple 연결만 못 끊고,
                    // 그건 아래 경고로 드러난다 — 로그인 자체를 500 으로 만드는 편이 훨씬 나쁘다.
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

    private IssuedToken issueTokens(AuthenticatedUser user, Set<AccountRole> roles) {
        Instant now = Instant.now();
        String refreshToken = tokenIssuer.generateRefreshToken();
        userPersistenceService.saveRefreshToken(
                user.userId(), tokenIssuer.hashRefreshToken(refreshToken), tokenIssuer.refreshTokenExpiry(now));
        return new IssuedToken(
                tokenIssuer.issueAccessToken(user.userId(), roles),
                refreshToken,
                tokenIssuer.accessTokenSeconds(),
                user.newUser());
    }

    /**
     * 이 provider 계정이 백오피스를 쓸 수 있는가(#342).
     *
     * <p>로그인은 평소대로 소셜로 하고 <b>역할만 올린다.</b> 어드민용 별도 로그인을 만들지 않는 이유는,
     * 그것이 곧 두 번째 자격증명 체계가 되어 지켜야 할 자리가 하나 더 늘기 때문이다.
     */
    private Set<AccountRole> rolesOf(AuthProvider provider, String subject) {
        return adminAccountRepository.find(provider, subject).isPresent() ? ADMIN_ROLES : APP_USER_ONLY;
    }

    /**
     * 재발급 경로용 — 토큰에는 subject 가 없어 신원을 한 번 더 읽는다.
     *
     * <p>개발 로그인 사용자는 신원이 없다. 그때는 일반 사용자로 본다 — 없는 것을 어드민으로 볼 이유가 없다.
     */
    private Set<AccountRole> rolesOf(UUID userId) {
        return userIdentityRepository
                .findFirstByUserId(userId)
                .map(identity -> rolesOf(identity.getProvider(), identity.getProviderUserId()))
                .orElse(APP_USER_ONLY);
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
