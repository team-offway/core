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
     * 탈퇴 — 이 사용자의 provider 연결을 모두 끊는다.
     *
     * <p>지우지 않으면 {@code (provider, provider_user_id)} UNIQUE 가 남아, 같은 사람이 다시 가입할 때
     * 없는 사용자를 가리키는 신원에 붙는다.
     *
     * @return 지운 행 수
     */
    int deleteByUserId(UUID userId);
}
