package com.offway.core.device.repository;

import com.offway.core.device.domain.DevicePushToken;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link DevicePushTokenRepositoryImpl} 이 위임한다. */
interface DevicePushTokenJpaRepository extends JpaRepository<DevicePushToken, Long> {

    List<DevicePushToken> findByGuestIdOrderByIdAsc(String guestId);

    /**
     * 등록·갱신을 한 문장으로 — MySQL 의 {@code ON DUPLICATE KEY UPDATE}.
     *
     * <p><b>JPA 로 풀 수 없는 자리라 native 다.</b> JPA 의 {@code save} 는 식별자로만 신규·기존을 가르는데,
     * 여기서 같은 것을 가르는 기준은 유니크 키 {@code (guest_id, token)} 이다. 애플리케이션에서 조회 후
     * 분기하면 동시 요청이 둘 다 "없다" 를 읽고 하나가 제약 위반으로 터진다.
     *
     * <p><b>소유자는 갱신 목록에 없다 — 유니크 키의 일부이기 때문이다.</b> 이 문장이 갱신으로 떨어지는
     * 것은 소유자까지 같을 때뿐이라 덮어쓸 것이 없다. 같은 토큰이 <b>다른</b> 소유자로 오면 키가 달라
     * 갱신이 아니라 새 행이 된다 — 남의 토큰을 아는 쪽이 그 행의 주인을 바꿔치기할 수 없다.
     *
     * <p>{@code AS incoming} 은 새 값을 가리키는 행 별칭이다. 예전 관용구인 {@code VALUES()} 함수는
     * MySQL 8.0.20 부터 deprecated 라 쓰지 않는다.
     *
     * <p>{@code created_at} 은 갱신 목록에 없다 — 재등록해도 처음 등록 시각은 남는다.
     *
     * <p>{@code clearAutomatically} 로 영속성 컨텍스트를 비운다. 이 문장은 JPA 를 우회하므로, 안 비우면
     * 같은 트랜잭션의 이어지는 조회가 갱신 전 스냅샷을 본다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO device_push_token (guest_id, token, platform, created_at, updated_at)
                    VALUES (:guestId, :token, :platform, :now, :now) AS incoming
                    ON DUPLICATE KEY UPDATE
                        platform = incoming.platform,
                        updated_at = incoming.updated_at
                    """,
            nativeQuery = true)
    void upsert(
            @Param("guestId") String guestId,
            @Param("token") String token,
            @Param("platform") String platform,
            @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DevicePushToken d where d.guestId = :guestId")
    int deleteByGuestId(@Param("guestId") String guestId);
}
