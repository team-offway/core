package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import java.time.Instant;
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
     * 회전 권리를 <b>선점</b>한다 — 살아 있고 만료되지 않은 토큰을 이번 호출이 폐기했을 때만 1.
     *
     * <p>읽고 검사하고 폐기하면 그 사이에 다른 요청이 같은 스냅샷을 읽어 <b>둘 다 회전에 성공</b>한다.
     * 토큰 하나에서 살아 있는 refresh 가 둘 나오고, 그 순간 재사용 감지의 보장이 무너진다. 판정과 기록을
     * 한 문장으로 합쳐 DB 가 갈라주게 한다({@code ExternalApiCallRepository.claimNotifyStep} 과 같은 방식).
     */
    int claimRotation(String tokenHash, Instant now);
}
