package com.offway.core.notification.repository;

import com.offway.core.notification.domain.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
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
                        notification.getUserId().toString(),
                        notification.getType().name(),
                        notification.course().orElse(null),
                        notification.getCreatedAt())
                > 0;
    }

    @Override
    public Page<Notification> findByOwner(UUID userId, Pageable pageable) {
        return notificationJpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }

    @Override
    public Optional<Notification> findOwned(UUID userId, Long id) {
        return notificationJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public long countUnread(UUID userId) {
        return notificationJpaRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public int markAllRead(UUID userId, LocalDateTime readAt) {
        return notificationJpaRepository.markAllRead(userId, readAt);
    }

    @Override
    public int markRead(UUID userId, Long id, LocalDateTime readAt) {
        return notificationJpaRepository.markRead(userId, id, readAt);
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        return notificationJpaRepository.deleteByUserId(userId);
    }
}
