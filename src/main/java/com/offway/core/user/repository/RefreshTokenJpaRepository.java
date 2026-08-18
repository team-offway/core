package com.offway.core.user.repository;

import com.offway.core.user.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA — 어댑터가 위임하는 실제 구현. */
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    int deleteByUserId(UUID userId);
    /**
     * 조건부 UPDATE 로 회전 권리를 선점한다 — 살아 있고 만료되지 않은 토큰을 이번 호출이 폐기했을 때만 1을 돌려준다.
     *
     * <p>파생 쿼리로는 표현할 수 없어 JPQL 을 직접 쓴다. {@code clearAutomatically} 가 필요한 이유는 이 벌크 UPDATE 가
     * 영속성 컨텍스트를 우회하기 때문이다 — 지우지 않으면 뒤이어 읽는 엔티티가 UPDATE 이전 상태로 나온다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now"
            + " where t.tokenHash = :tokenHash and t.revokedAt is null and t.expiresAt > :now")
    int claimRotation(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /**
     * 이 사용자의 살아 있는 토큰을 <b>한 문장으로</b> 폐기한다 — 로그아웃·재사용 감지.
     *
     * <p>읽어서 하나씩 고치면 행 수만큼 UPDATE 가 나가고, 바뀌지 않은 {@code token_hash} 까지 다시 써서
     * UNIQUE 인덱스가 함께 갱신된다. 이 표는 삭제 경로가 없어 사용자당 행이 계속 쌓이는 자리라 그 차이가 크다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
