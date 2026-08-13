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

    /**
     * 탈퇴 — 이 사용자의 refresh 를 폐기가 아니라 <b>삭제</b>한다.
     *
     * <p>평소에는 지우지 않고 {@code revoked_at} 을 채운다. 지우면 "폐기된 토큰 재사용" 과 "없는 토큰" 이
     * 구분되지 않아 탈취를 감지할 수 없기 때문이다. 탈퇴는 다르다 — 계정 자체가 사라져 재사용을 감지해서
     * 보호할 대상이 없고, 남겨두면 주인 없는 개인정보만 쌓인다.
     *
     * @return 지운 행 수
     */
    int deleteByUserId(UUID userId);
}
