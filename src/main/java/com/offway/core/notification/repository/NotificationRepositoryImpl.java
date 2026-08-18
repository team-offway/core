package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** port 구현(adapter) — Spring Data 에 위임. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;

    @Override
    public Notification save(Notification notification) {
        return notificationJpaRepository.save(notification);
    }

    /**
     * <b>한 건이 자기 트랜잭션을 갖는다.</b> 배치가 수백 건을 도는데 한 건의 실패가 나머지를 물면 안 된다.
     *
     * <p>{@code @Modifying} native 질의라 트랜잭션이 필요하고, 여기서 경계를 끊어야 호출자가 실패를 잡고
     * 다음 건으로 넘어갈 수 있다. 호출자 트랜잭션 안에서 잡으면 그 트랜잭션이 이미 rollback-only 라
     * 커밋 시점에 터진다.
     */
    @Override
    @Transactional
    public boolean saveIfAbsent(Notification notification) {
        return notificationJpaRepository.insertIfAbsent(
                        notification.getGuestId(),
                        notification.getType().name(),
                        notification.course().orElse(null),
                        notification.getCreatedAt())
                > 0;
    }

    @Override
    public Page<Notification> findByOwner(String guestId, Pageable pageable) {
        return notificationJpaRepository.findByGuestIdOrderByCreatedAtDescIdDesc(guestId, pageable);
    }

    @Override
    public Optional<Notification> findOwned(String guestId, Long id) {
        return notificationJpaRepository.findByIdAndGuestId(id, guestId);
    }

    @Override
    public long countUnread(String guestId) {
        return notificationJpaRepository.countByGuestIdAndReadAtIsNull(guestId);
    }

    @Override
    public int markAllRead(String guestId, LocalDateTime readAt) {
        return notificationJpaRepository.markAllRead(guestId, readAt);
    }

    @Override
    public int markRead(String guestId, Long id, LocalDateTime readAt) {
        return notificationJpaRepository.markRead(guestId, id, readAt);
    }
}
