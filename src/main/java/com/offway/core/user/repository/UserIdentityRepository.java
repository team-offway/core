package com.offway.core.user.repository;

import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.UserIdentity;
import java.util.Optional;

/** provider 신원 매핑 영속 port. 구현은 {@link UserIdentityRepositoryImpl}. */
public interface UserIdentityRepository {

    UserIdentity save(UserIdentity identity);

    /** provider + sub 로 기존 연결을 찾는다. 이메일이 아니라 sub 이 매칭 키다. */
    Optional<UserIdentity> findByProviderAndSubject(AuthProvider provider, String subject);
}
