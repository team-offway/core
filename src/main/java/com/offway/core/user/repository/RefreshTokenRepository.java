package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import com.offway.core.user.domain.RevokedReason;
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
     * 탈퇴 — 이 사용자의 refresh 를 폐기가 아니라 <b>삭제</b>한다.
     *
     * <p>평소에는 지우지 않고 {@code revoked_at} 을 채운다. 지우면 "폐기된 토큰 재사용" 과 "없는 토큰" 이
     * 구분되지 않아 탈취를 감지할 수 없기 때문이다. 탈퇴는 다르다 — 계정 자체가 사라져 재사용을 감지해서
     * 보호할 대상이 없고, 남겨두면 주인 없는 개인정보만 쌓인다.
     *
     * @return 지운 행 수
     */
    int deleteByUserId(UUID userId);

    /**
     * 회전 권리를 <b>선점</b>한다 — 살아 있고 만료되지 않은 토큰을 이번 호출이 폐기했을 때만 1.
     *
     * <p>읽고 검사하고 폐기하면 그 사이에 다른 요청이 같은 스냅샷을 읽어 <b>둘 다 회전에 성공</b>한다.
     * 토큰 하나에서 살아 있는 refresh 가 둘 나오고, 그 순간 재사용 감지의 보장이 무너진다. 판정과 기록을
     * 한 문장으로 합쳐 DB 가 갈라주게 한다({@code ExternalApiCallRepository.claimNotifyStep} 과 같은 방식).
     */
    int claimRotation(String tokenHash, Instant now);

    /**
     * 이 사용자의 살아 있는 토큰을 전부 폐기한다 — 로그아웃·재사용 감지.
     *
     * @return 폐기한 행 수
     */
    int revokeActive(UUID userId, Instant now, RevokedReason reason);

    /**
     * <b>이 세션 하나만</b> 폐기한다 — 기기별 로그아웃(#389).
     *
     * <p>{@code userId} 를 조건에 함께 넣는다. 해시만으로 지우면 남의 토큰 원문을 아는 사람이 그 사람을
     * 로그아웃시킬 수 있다.
     *
     * @return 폐기한 행 수. 0 이면 없거나 이미 폐기됐거나 남의 것이다
     */
    int revokeOne(UUID userId, String tokenHash, Instant now);

}
