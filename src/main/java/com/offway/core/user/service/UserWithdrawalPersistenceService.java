package com.offway.core.user.service;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.event.UserWithdrawn;
import com.offway.core.user.repository.RefreshTokenRepository;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴의 <b>DB 작업만</b> 담는 빈(#287).
 *
 * <p><b>왜 갈랐나.</b> 탈퇴가 Apple 연결 해제(외부 호출)를 하게 되면서 한 메서드에 트랜잭션과 외부 호출이
 * 섞였다. 같은 빈 안에서 {@code @Transactional} 메서드를 부르면 Spring AOP proxy 를 거치지 않아 트랜잭션이
 * <b>조용히 무력화된다</b>(persistence-convention §self-invocation). 경계를 나누려면 빈을 나눠야 한다.
 *
 * <p>외부 호출을 트랜잭션 안에 두면 안 되는 이유는 그 밖에도 있다 — Apple 응답을 기다리는 동안 DB 커넥션을
 * 잡아 풀이 마르고, Apple 실패가 삭제를 롤백시켜 <b>계정을 지울 권리가 외부 서비스 상태에 묶인다</b>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalPersistenceService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 연결 해제에 쓸 Apple 토큰 — <b>지우기 전에</b> 읽어야 한다.
     *
     * <p><b>없는 것이 정상인 경우가 있다.</b> 이 기능 이전에 로그인한 사용자, {@code authorizationCode} 를
     * 안 보내는 옛 앱, Apple 이 아닌 provider. 소급해서 채울 수 없다(코드는 1회용·5분) — 재로그인하면 채워진다.
     */
    @Transactional(readOnly = true)
    public Optional<ProviderLink> appleLinkOf(UUID userId) {
        return userIdentityRepository
                .findByUserIdAndProvider(userId, AuthProvider.APPLE)
                .filter(UserIdentity::revocable)
                .map(identity -> new ProviderLink(identity.getProviderRefreshToken(), identity.getProviderClientId()));
    }

    /**
     * 계정과 그 사람의 데이터를 지운다.
     *
     * <p><b>전부 한 트랜잭션이다.</b> 도메인별 정리는 {@link UserWithdrawn} 리스너들이 하는데, 동기로 돌아 같은
     * 트랜잭션에 참여한다. 하나라도 실패하면 통째로 롤백되고 사용자는 실패를 본다 — 부분 성공하면 사용자 행만
     * 사라지고 코스·연차가 <b>소유자 없이 남아 다시는 지울 수 없는</b> 데이터가 된다.
     *
     * <p>순서가 중요하다. 이벤트를 먼저 보내 도메인들이 자기 데이터를 치운 뒤에 계정을 지운다.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        // 토큰은 유효한데 계정이 없는 경우가 실제로 있다 — access 가 무상태라 탈퇴 후 만료까지 살아 있다.
        // 없는 계정에 삭제를 또 태우면 200 이 나가 앱이 "탈퇴됐다" 고 오해한다.
        if (userRepository.findById(userId).isEmpty()) {
            throw UserException.withdrawnUser();
        }
        // 대상을 찾는 단계가 사라졌다(#280). 예전에는 게스트 키를 모아 그 수만큼 이벤트를 보냈고,
        // 헤더를 안 보낸 앱은 키가 없어 코스·연차가 주인 없이 남았다 — 그 경고를 남겨야 했던 이유다.
        // 이제 소유가 이 사용자라 "대상을 못 찾는 탈퇴" 자체가 없다.
        eventPublisher.publishEvent(new UserWithdrawn(userId));
        int identities = userIdentityRepository.deleteByUserId(userId);
        int tokens = refreshTokenRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
        // 식별자만 남긴다 — 닉네임·이메일은 지우는 마당에 로그로 옮겨 적을 이유가 없다.
        log.info("회원 탈퇴 완료 userId={} 신원 {}건 · refresh {}건 삭제", userId, identities, tokens);
    }

    /**
     * 연결 해제에 필요한 두 값.
     *
     * <p>엔티티를 트랜잭션 밖으로 들고 나가지 않는다 — 지연 로딩이 detached 상태에서 터지고, 무엇보다
     * 외부 호출 코드가 도메인 객체를 고칠 수 있게 된다.
     */
    public record ProviderLink(String refreshToken, String clientId) {}
}
