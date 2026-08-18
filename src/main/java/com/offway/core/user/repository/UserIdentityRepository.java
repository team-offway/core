package com.offway.core.user.repository;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserIdentity;
import java.util.Optional;
import java.util.UUID;

/** provider 신원 매핑 영속 port. 구현은 {@link UserIdentityRepositoryImpl}. */
public interface UserIdentityRepository {

    UserIdentity save(UserIdentity identity);

    /** provider + sub 로 기존 연결을 찾는다. 이메일이 아니라 sub 이 매칭 키다. */
    Optional<UserIdentity> findByProviderAndSubject(AuthProvider provider, String subject);

    /**
     * 이 사용자가 어느 provider 로 로그인했는지 — 내 정보 조회가 쓴다(#282).
     *
     * <p><b>없을 수 있다.</b> local 개발 로그인({@code /auth/dev-login})은 provider 연결 없이 사용자만
     * 만든다. 그 경우를 예외로 다루면 로컬에서 이 조회가 통째로 깨진다.
     */
    Optional<UserIdentity> findFirstByUserId(UUID userId);

    /**
     * 이 사용자의 특정 provider 신원 — 연결 해제용 토큰을 읽고 쓰는 자리(#287).
     *
     * <p>{@link #findFirstByUserId} 와 달리 provider 를 좁힌다. Apple 토큰을 카카오 신원에 적으면 해제할 때
     * 엉뚱한 곳에 서명을 보낸다.
     */
    Optional<UserIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    /**
     * 탈퇴 — 이 사용자의 provider 연결을 모두 끊는다.
     *
     * <p>지우지 않으면 {@code (provider, provider_user_id)} UNIQUE 가 남아, 같은 사람이 다시 가입할 때
     * 없는 사용자를 가리키는 신원에 붙는다.
     *
     * @return 지운 행 수
     */
    int deleteByUserId(UUID userId);
}
