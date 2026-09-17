package com.offway.core.liveactivity.repository;

import com.offway.core.liveactivity.domain.PushToStartToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link PushToStartTokenRepositoryImpl} 이 위임한다. */
interface PushToStartTokenJpaRepository extends JpaRepository<PushToStartToken, Long> {

    List<PushToStartToken> findAllByOrderByIdAsc();

    /**
     * 등록·갱신을 한 문장으로 — MySQL 의 {@code ON DUPLICATE KEY UPDATE}.
     *
     * <p><b>JPA 로 풀 수 없는 자리라 native 다.</b> {@code save} 는 식별자로만 신규·기존을 가르는데,
     * 여기서 같은 것을 가르는 기준은 유니크 키 {@code (user_id, token)} 이다. 조회 후 분기하면 동시
     * 요청이 둘 다 "없다" 를 읽고 하나가 제약 위반으로 터진다.
     *
     * <p><b>소유자는 문자열로 받아 {@code UUID_TO_BIN} 으로 바꾼다.</b> native 문장은 엔티티 매핑
     * ({@code @JdbcTypeCode(BINARY)})을 타지 않아, {@code UUID} 를 그대로 넘기면 어떤 바이트로
     * 바인딩될지가 드라이버·방언에 달린다. 컬럼이 {@code BINARY(16)} 인 이상 그 인코딩은 추측이
     * 아니라 문장 안에 적혀 있어야 한다(#280 에서 {@code notification} 이 같은 이유로 이렇게 짰다).
     *
     * <p>{@code created_at} 은 갱신 목록에 없다 — 앱이 다시 보내도 처음 등록 시각은 남는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO push_to_start_token (user_id, token, created_at, updated_at)
                    VALUES (UUID_TO_BIN(:userId), :token, :now, :now) AS incoming
                    ON DUPLICATE KEY UPDATE
                        updated_at = incoming.updated_at
                    """,
            nativeQuery = true)
    void upsert(
            @Param("userId") String userId, @Param("token") String token, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushToStartToken t where t.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /** 행 id 로 지운다 — 죽은 토큰 정리. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushToStartToken t where t.id = :id")
    int deleteRow(@Param("id") Long id);
}
