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
