package com.offway.core.user.repository;

import com.offway.core.user.domain.AdminAccount;
import com.offway.core.user.domain.AuthProvider;
import java.util.Optional;

/** 도메인이 의존하는 port. 구현은 {@link AdminAccountRepositoryImpl}. */
public interface AdminAccountRepository {

    /** 이 provider 계정이 백오피스를 쓸 수 있는가. 없으면 빈 값 — 일반 사용자다. */
    Optional<AdminAccount> find(AuthProvider provider, String providerUserId);
}
