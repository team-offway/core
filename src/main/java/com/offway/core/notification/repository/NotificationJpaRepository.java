package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data — {@link NotificationRepositoryImpl} 이 위임한다. */
interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    /**
     * 정렬은 쿼리가 소유한다({@code Paging.of} 가 정렬을 얹지 않는 이유).
     *
     * <p>{@code id} 를 2차 정렬에 둔다 — 같은 초에 만들어진 알림(한 배치가 여러 건을 넣는다)의 순서가
     * 정해지지 않으면 페이지 경계에서 같은 행이 두 번 나오거나 아예 빠진다.
     */
    Page<Notification> findByGuestIdOrderByCreatedAtDescIdDesc(String guestId, Pageable pageable);

    Optional<Notification> findByIdAndGuestId(Long id, String guestId);

    /**
     * 유니크 키 {@code (guest_id, type, course_id)} 가 이미 있으면 아무것도 하지 않는다.
     *
     * <p>{@code save} 로는 안 된다 — 식별자로만 신규·기존을 가르는데, 여기서 같은 것을 가르는 기준은
     * 그 유니크 키다. 그래서 native 다.
     *
     * <p><b>삽입 조건을 문장 안에 둔다.</b> 처음에는 {@code ON DUPLICATE KEY UPDATE id = id} 로 짰는데,
     * 그 관용구로는 "새로 만들었나" 를 영향 행수로 가를 수 없었다 — MySQL JDBC 는 기본이
     * {@code useAffectedRows=false} 라 실제 변경 행이 아니라 <b>매칭된 행</b>을 돌려주므로, 중복이라
     * 아무것도 안 바뀐 경우에도 1 이 온다. 그래서 재실행이 "새로 1건 만들었다" 고 거짓 보고했다.
     *
     * <p>{@code NOT EXISTS} 로 바꾸면 삽입이 일어난 경우에만 1, 아니면 0 이라 그 값이 곧 답이 된다.
     * 조회와 삽입 사이의 경쟁은 유니크 키가 최종 방어로 남는다(그때는 예외 → 롤백 → 다음 실행이 채운다).
     *
     * <p>{@code INSERT IGNORE} 를 쓰지 않는 이유는 그것이 중복 외의 오류(길이 초과·타입 불일치)까지
     * 경고로 낮춰 삼키기 때문이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO notification (guest_id, type, course_id, created_at)
                    SELECT :guestId, :type, :courseId, :createdAt
                    WHERE NOT EXISTS (
                        SELECT 1 FROM notification existing
                        WHERE existing.guest_id = :guestId
                          AND existing.type = :type
                          AND existing.course_id <=> :courseId
                    )
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("guestId") String guestId,
            @Param("type") String type,
            @Param("courseId") Long courseId,
            @Param("createdAt") LocalDateTime createdAt);

    long countByGuestIdAndReadAtIsNull(String guestId);

    /**
     * 전체 읽음은 <b>벌크 UPDATE</b> 다 — 행을 다 읽어 하나씩 고치면 쌓인 만큼 힙에 올린다.
     *
     * <p>{@code clearAutomatically} 로 영속성 컨텍스트를 비운다. 안 비우면 같은 트랜잭션에서 이어지는
     * 안읽음 개수 조회가 갱신 전 스냅샷을 보고 0 이 아닌 값을 답한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.guestId = :guestId and n.readAt is null")
    int markAllRead(@Param("guestId") String guestId, @Param("readAt") LocalDateTime readAt);
}
