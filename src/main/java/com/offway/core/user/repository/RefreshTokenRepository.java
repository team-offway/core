package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** refresh 토큰 영속 port. 구현은 {@link RefreshTokenRepositoryImpl}. */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    /** 해시로 찾는다. 폐기된 것도 돌려줘야 재사용(탈취) 감지가 가능하다. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 아직 살아 있는 토큰들 — 로그아웃·재사용 감지 시 일괄 폐기 대상. */
    List<RefreshToken> findActiveByUserId(UUID userId);
}
